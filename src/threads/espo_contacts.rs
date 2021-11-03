use std::sync::Arc;
use espocrm_rs::{EspoApiClient, Method};
use crate::env::AppData;
use crate::RT;
use crate::try_rl;
use anyhow::Result;
use log::{debug, error};
use mysql::{PooledConn, Row};
use mysql::prelude::Queryable;
use ratelimit_meter::{DirectRateLimiter, LeakyBucket};
use serde::{Serialize, Deserialize};
use nonzero_ext::nonzero;

/// The maximum amount of records that should be returned in 1 request to EspoCRM
const ESPO_MAX_SIZE: i64 = 100;

const FAILED_INTERVAL: u64 = 300; // 5 minutes
const SUCCESS_INTERVAL: u64 = 1800; // 30 minutes

/// The path where the inbox is located. This will be joined with the basepath and the query parameters
const INBOX_PATH: &str = "/";

pub fn espo_contacts(data: Arc<AppData>) {
    std::thread::spawn(move || {
        let _guard = RT.enter();

        debug!("EspoCRM is at {}", &data.env.espo_host);

        loop {
            log::info!("Starting contact loop");
            match RT.block_on(update_contacts(data.clone())) {
                Ok(_) => {},
                Err(e) => {
                    error!("Failed to update contacts: {:?}", e);
                    sleep(FAILED_INTERVAL);
                    continue;
                }
            }

            sleep(SUCCESS_INTERVAL);
        }
    });
}

/// Update all contacts with the link to the respective inbox
async fn update_contacts(data: Arc<AppData>) -> Result<()> {
    debug!("Updating contacts");

    let mut rl_bucket = DirectRateLimiter::<LeakyBucket>::per_second(nonzero!(2u32));

    let contacts = fetch_contacts(&data.espo, &mut rl_bucket).await?;
    let frontend_basepath = get_frontend_basepath(&mut data.pool.get_conn()?)?;
    for contact in contacts {
        if let Some(email) = &contact.email_address {

            // We don't want to throw Espo to death with too many requests
            // Hence we're using a ratelimiter
            let url = format!("{}{}?ref={}", frontend_basepath, INBOX_PATH, &email);
            try_rl!(rl_bucket, update_contact_mailsync_url(&data.espo, &contact, &url)).await?;
        } else {
            log::warn!("Contact '{}' is missing an E-Mail address", &contact.id);
        }
    }

    Ok(())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct UpdateContactMailsync<'a> {
    mail_sync: &'a str
}

/// Update the mailsync url of a contact
async fn update_contact_mailsync_url(client: &EspoApiClient, contact: &Contact, basepath: &str) -> Result<()> {
    debug!("Updating mailsync url of contact {}", &contact.id);
    let payload = UpdateContactMailsync { mail_sync: basepath };
    client.request(Method::Put, format!("Contact/{}", &contact.id), None, Some(&payload)).await?;

    Ok(())
}

/// Get the configured frontend basepath
fn get_frontend_basepath(conn: &mut PooledConn) -> Result<String> {
    debug!("Fetching frontend_basepath from database");

    let row: Option<Row> = conn.exec_first("SELECT value FROM configs WHERE name = 'frontend_basepath'", mysql::Params::Empty)?;
    match row {
        Some(r) => Ok(r.get("value").unwrap()),
        None => Ok(String::default())
    }
}

#[derive(Deserialize)]
struct FetchContacts {
    list:   Vec<Contact>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Contact {
    id:             String,
    email_address:  Option<String>
}

/// Fetch all contacts registered in Espo
async fn fetch_contacts(client: &EspoApiClient, dl_bucket: &mut DirectRateLimiter) -> Result<Vec<Contact>> {
    let mut offset = 0;
    let mut prev_total = 1;

    let mut result = Vec::new();
    while prev_total != 0 {
        debug!("Querying Espo at offset {}", offset);

        let params = espocrm_rs::Params::default()
            .set_select("id,emailAddress")
            .set_max_size(ESPO_MAX_SIZE)
            .set_offset(offset * ESPO_MAX_SIZE)
            .build();

        let mut response: FetchContacts = try_rl!(dl_bucket, client.request::<(), &str>(Method::Get, "Contact", Some(params), None))
            .await?
            .json()
            .await?;

        debug!("Got {} records", response.list.len());

        prev_total = response.list.len();
        result.append(&mut response.list);
        offset += 1;
    }

    Ok(result)
}

/// Sleep for `s` seconds
fn sleep(s: u64) {
    debug!("Sleeping for {} seconds", s);
    let dur = std::time::Duration::from_secs(s);
    std::thread::sleep(dur);
}