use crate::env::{AppData, Env};
use crate::api::gmail::{self, ListUserMessagesQuery};
use crate::RT;
use crate::try_rl;

use std::collections::HashSet;
use std::sync::Arc;
use mysql::{PooledConn, params, Row, TxOpts};
use mysql::prelude::Queryable;
use mailparse::MailHeaderMap;
use anyhow::Result;
use log::{debug, error, info};
use ratelimit_meter::{DirectRateLimiter, LeakyBucket};
use nonzero_ext::nonzero;
use log::warn;

/// Time in between loop iterations in seconds, where the previous iteration did not complete successfully.
const FAILED_INTERVAL: u64 = 300; // 5 minutes

/// Time in between loops in seconds, where the previous iteration completed successfuly.
const SUCCESS_INTERVAL: u64 = 1800; // 30 minutes

/// Amount of requests to the GMail API per second
const REQUESTS_PER_SECOND: u32 = 10;

/// Inbox querying loop
///
/// This loop will fetch all users periodically and check for new messages in their inbox
pub fn query_inbox(appdata: Arc<AppData>) {
    std::thread::spawn(move || {
        let _guard = RT.enter();
        loop {
            match RT.block_on(do_inbox_query(appdata.clone())) {
                Ok(_) => {},
                Err(e) => {
                    error!("Failed to perform inbox query: {:?}", e);
                    sleep(FAILED_INTERVAL);
                    continue;
                }
            }
            sleep(SUCCESS_INTERVAL);
        }
    });
}

/// Query the inbox of all registered users
async fn do_inbox_query(data: Arc<AppData>) -> Result<()> {
    info!("Starting new round of inbox querying.");

    let mut rl_bucket = DirectRateLimiter::<LeakyBucket>::per_second(nonzero!(REQUESTS_PER_SECOND));

    let env = &data.env;
    debug!("Creating mysql connection");
    let mut conn = data.pool.get_conn()?;

    debug!("Fetching users");
    let users = get_users(&mut conn)?;

    for user in users {
        debug!("Querying for user {}", &user);
        match process_inbox(&user, env, &mut conn, &mut rl_bucket).await {
            Ok(_) => {},
            Err(e) => {
                warn!("Failed to query inbox for user '{}': {:?}", &user, &e);
                continue;
            }
        }
    }

    Ok(())
}

/// Fetch the users that should have their inbox fetched
fn get_users(conn: &mut PooledConn) -> Result<Vec<String>> {
    let rows: Vec<Row> = conn.exec("SELECT id FROM users", mysql::Params::Empty)?;
    Ok(rows.into_iter()
        .map(|f| f.get("id").unwrap())
        .collect())
}

/// Sleep for `s` seconds
fn sleep(s: u64) {
    debug!("Sleeping for {} seconds", s);
    let dur = std::time::Duration::from_secs(s);
    std::thread::sleep(dur);
}

/// Query a User's inbox and insert it into the database
async fn process_inbox(user_id: &str, env: &Env, conn: &mut PooledConn, rl_bucket: &mut DirectRateLimiter) -> Result<()> {
    debug!("Fething processed messages for {}", user_id);
    let already_processed = get_processed_messages(user_id, conn)?;
    debug!("Indexing inbox for {}", user_id);
    let inbox = index_inbox(user_id, env, rl_bucket).await?;

    let already_processed: HashSet<String> = already_processed.into_iter().collect();
    let inbox: HashSet<String> = inbox.into_iter().collect();

    let delta = &inbox - &already_processed;
    debug!("Found {} new messages for {}.", delta.len(), user_id);

    for id in delta {
        debug!("Fetching message {} for user {}", id, user_id);
        let message = match try_rl!(rl_bucket, get_message(user_id, &id, env)).await {
            Ok(m) => m,
            Err(e) => {
                warn!("Failed to fetch message '{}' for user '{}': {:?}", &id, &user_id, &e);
                continue;
            }
        };

        insert_message(user_id, &id, message, conn)?;
    }

    Ok(())
}

struct Message {
    sender:     Option<String>,
    receiver:   Option<String>,
    cc:         Option<String>,
    bcc:        Option<String>,
    subject:    Option<String>,
    body:       Option<String>,
    body_mime:  String,
    timestamp:  i64,
}

/// Insert a message into the database
fn insert_message(user_id: &str, message_id: &str, message: Message, conn: &mut PooledConn) -> Result<()> {
    let mut tr = conn.start_transaction(TxOpts::default())?;

    tr.exec_drop("INSERT INTO messages (id, sender, receiver, subject, cc, bcc, body, body_mime, timestamp) VALUES (:id, :sender, :receiver, :subject, :cc, :bcc, :body, :body_mime, :timestamp)", params! {
        "id" => &message_id,
        "sender" => &message.sender,
        "receiver" => &message.receiver,
        "subject" => &message.subject,
        "cc" => &message.cc,
        "bcc" => &message.bcc,
        "body" => &message.body,
        "body_mime" => &message.body_mime,
        "timestamp" => &message.timestamp,
    })?;

    tr.exec_drop("INSERT INTO messages_processed (id, user_id) VALUES (:id, :user_id)", params! {
        "id" => &message_id,
        "user_id" => &user_id
    })?;

    tr.commit()?;
    Ok(())
}

/// Get a message from GMail
async fn get_message(user_id: &str, message_id: &str, env: &Env) -> Result<Message> {
    debug!("Fetching message {}", message_id);
    let msg = gmail::get_user_messages(env, user_id, message_id)
        .await?;

    let timestamp: i64 = msg.internal_date.parse()?;

    debug!("Parsing message {}", message_id);

    let raw = msg.raw
        .replace('-', "+")
        .replace('_', "/");
    let raw = base64::decode(raw)?;

    let parsed = mailparse::parse_mail(&raw)?;
    let opt_body = parsed.get_body()?;
    let ctype = parsed.ctype.mimetype;

    let (body, mimetype) = if !parsed.subparts.is_empty() {
        let mut maybe_html = None;
        let mut maybe_text = None;

        // Find the HTML body and/org the plaintext body
        'parts: for part in parsed.subparts {
            if part.ctype.mimetype.eq("text/html") {
                maybe_html = Some(part.get_body()?);
                break 'parts;
            }

            if part.ctype.mimetype.eq("text/plain") {
                maybe_text = Some(part.get_body()?);
            }
        }

        // If there's an HTML body, we use that, else we use a text body, if both dont exist we use an empty String
        match (maybe_html, maybe_text) {
            (Some(a), Some(_)) | (Some(a), None) => (a, String::from("text/html")),
            (None, Some(a)) => (a, String::from("text/plain")),
            (None, None) => (opt_body, ctype)
        }
    } else {
        (opt_body, ctype)
    };

    let headers = parsed.headers;

    let sender = match headers.get_first_value("From") {
        Some(s) => Some(s
            .replace('"', "")
            .replace('\'', "")),
        None => None
    };

    let receiver = match headers.get_first_value("To") {
        Some(s) => Some(s
            .replace('"', "")
            .replace('\'', "")),
        None => None
    };

    Ok(Message {
        sender,
        receiver,
        cc: headers.get_first_value("Cc"),
        bcc: headers.get_first_value("Bcc"),
        subject: headers.get_first_value("Subject"),
        body: Some(body),
        body_mime: mimetype,
        timestamp,
    })
}

/// Index a user's inbox from GMail
async fn index_inbox(user_id: &str, env: &Env, rl_bucket: &mut DirectRateLimiter) -> Result<Vec<String>> {
    let mut result: Vec<String> = Vec::new();
    let mut page_token: Option<String> = None;

    let mut page_counter = 0;
    loop {
        debug!("Reading inbox page {} for user {}", page_counter, user_id);

        let query = ListUserMessagesQuery { max_results: Some(100), page_token: page_token.as_deref() };
        let index = try_rl!(rl_bucket, gmail::list_user_messages(env, user_id, &query)).await?;

        page_token = index.next_page_token;
        let mut ids: Vec<String> = index.messages.into_iter()
            .map(|f| f.id)
            .collect();

        result.append(&mut ids);

        if page_token.is_none() {
            break;
        }

        page_counter += 1;
    }

    Ok(result)
}

/// Fetch a list of IDs of messages that have already been processed
fn get_processed_messages(user_id: &str, conn: &mut PooledConn) -> Result<Vec<String>> {
    let rows: Vec<Row> = conn.exec("SELECT id FROM messages_processed WHERE user_id = :user_id", params! {
        "user_id" => &user_id
    })?;

    Ok(rows.into_iter()
        .map(|f| f.get("id").unwrap())
        .collect())
}