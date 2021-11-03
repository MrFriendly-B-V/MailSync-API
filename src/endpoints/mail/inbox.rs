use crate::env::AppData;
use crate::check_scopes;
use crate::error::HttpResult;
use crate::RT;

use actix_web::{web, get, HttpRequest, HttpResponse};
use sea_query::{Query, any, Expr, MysqlQueryBuilder};
use serde::{Serialize, Deserialize};
use mysql::prelude::Queryable;
use mysql::{Row, PooledConn};
use std::ops::Deref;
use std::fmt::Write;
use std::sync::Arc;
use anyhow::Result;
use log::debug;

const DEFAULT_PAGE: i32 = 0;
const DEFAULT_PAGE_SIZE: i32 = 50;
const DEFAULT_ORDER_BY: OrderBy = OrderBy::Timestamp;
const DEFAULT_ORDER: Order = Order::Desc;

const REQUIRED_SCOPES: [&str; 1] = ["mrfriendly.mailsync.mail.inbox"];

#[derive(Serialize)]
pub struct Inbox {
    messages:   Vec<ResponseMessage>,
    amount:     i64,
    total:      i64,
}

#[derive(Serialize)]
pub struct ResponseMessage {
    id:         String,
    subject:    Option<String>,
    to:         Option<String>,
    from:       Option<String>,
    cc:         Option<String>,
    bcc:        Option<String>,
    timestamp:  i64,
}

#[derive(Deserialize)]
pub struct QueryParams {
    page:       Option<i32>,
    page_size:  Option<i32>,
    q:          Option<String>,
    order_by:   Option<OrderBy>,
    order:      Option<Order>
}

#[derive(Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Order {
    Asc,
    Desc,
}

#[derive(Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum OrderBy {
    Subject,
    To,
    From,
    Cc,
    Bcc,
    Timestamp
}

#[get("/mail/inbox")]
pub async fn inbox(data: web::Data<Arc<AppData>>, req: HttpRequest, query_params: web::Query<QueryParams>) -> HttpResult {
    let _guard = RT.enter();
    check_scopes!(req, data, REQUIRED_SCOPES);

    debug!("Creating mysql connection");
    let mut conn = data.pool.get_conn()?;

    debug!("Fetching the amount of messages that match the query '{:?}'", &query_params.q);
    let total = calculate_total(&mut conn, query_params.q.as_deref())?;

    debug!("Fetching messages");
    let messages = get_messages(&mut conn, &query_params)?;

    let inbox = Inbox {
        amount: messages.len() as i64,
        messages,
        total,
    };

    Ok(HttpResponse::Ok().json(&inbox))
}

/// Get the messages matching the current query, sorting and pagination
fn get_messages(conn: &mut PooledConn, query_params: &QueryParams) -> Result<Vec<ResponseMessage>> {
    let mut select_stmt = Query::select();
    select_stmt
        .columns(vec![MessagesTable::Id, MessagesTable::Sender, MessagesTable::Receiver, MessagesTable::Subject, MessagesTable::Cc, MessagesTable::Bcc, MessagesTable::Timestamp])
        .from(MessagesTable::Table);

    if let Some(q) = &query_params.q {
        select_stmt
            .cond_where(any![
                Expr::col(MessagesTable::Sender).like(&format!("%{}%", q)),
                Expr::col(MessagesTable::Receiver).like(&format!("%{}%", q)),
                Expr::col(MessagesTable::Subject).like(&format!("%{}%", q)),
                Expr::col(MessagesTable::Cc).like(&format!("%{}%", q)),
                Expr::col(MessagesTable::Bcc).like(&format!("%{}%", q))
            ]);
    }

    match (&query_params.order, &query_params.order_by) {
        (Some(order), Some(order_by)) => select_stmt.order_by(MessagesTable::from(order_by), order.into()),
        (Some(order), None) => select_stmt.order_by(MessagesTable::from(DEFAULT_ORDER_BY), order.into()),
        (None, Some(order_by)) => select_stmt.order_by(MessagesTable::from(order_by), DEFAULT_ORDER.into()),
        (None, None) => &mut select_stmt
    };

    match (&query_params.page, &query_params.page_size) {
        (Some(page), Some(page_size)) => {
            if *page > 0 && *page_size > 0 {
                debug!("Offset: {}", page * page_size);
                select_stmt
                    .offset((page * page_size) as u64)
                    .limit(*page_size as u64);
            }
        },
        (Some(page), None) => {
            if *page > 0 {
                debug!("Offset: {}", page * DEFAULT_PAGE_SIZE);
                select_stmt
                    .offset((page * DEFAULT_PAGE_SIZE) as u64)
                    .limit(DEFAULT_PAGE_SIZE as u64);
            }
        },
        (None, Some(page_size)) => {
            if *page_size > 0 {
                debug!("Offset: {}", page_size * DEFAULT_PAGE);
                select_stmt
                    .offset((page_size * DEFAULT_PAGE) as u64)
                    .limit(*page_size as u64);
            }
        },
        (None, None) => {
            debug!("Offset: {}", DEFAULT_PAGE * DEFAULT_PAGE_SIZE);
            select_stmt
                .offset((DEFAULT_PAGE * DEFAULT_PAGE_SIZE) as u64)
                .limit(DEFAULT_PAGE_SIZE as u64);
        }
    }

    let (sql_query, values) = select_stmt.build(MysqlQueryBuilder);
    let rows: Vec<Row> = conn.exec(sql_query, MysqlParams(values))?;

    let messages: Vec<ResponseMessage> = rows.into_iter()
        .map(|f| {
            ResponseMessage {
                id: f.get("id").unwrap(),
                subject: f.get("subject").unwrap(),
                to: f.get("receiver").unwrap(),
                from: f.get("sender").unwrap(),
                cc: f.get("cc").unwrap(),
                bcc: f.get("bcc").unwrap(),
                timestamp: f.get("timestamp").unwrap()
            }
        })
        .collect();

    Ok(messages)
}

/// Calculate the total amount of Messages available that match the current query, if there is any
fn calculate_total(conn: &mut PooledConn, q: Option<&str>) -> Result<i64> {
    let mut sea_query = Query::select();
    sea_query
        .columns(vec![MessagesTable::Id])
        .from(MessagesTable::Table);

    if let Some(q) = q {
        sea_query
            .cond_where(any![
                Expr::col(MessagesTable::Sender).like(&format!("%{}%", q)),
                Expr::col(MessagesTable::Receiver).like(&format!("%{}%", q)),
                Expr::col(MessagesTable::Subject).like(&format!("%{}%", q)),
                Expr::col(MessagesTable::Cc).like(&format!("%{}%", q)),
                Expr::col(MessagesTable::Bcc).like(&format!("%{}%", q))
            ]);
    }

    let (sql_query, values) = sea_query.build(MysqlQueryBuilder);
    let rows: Vec<Row> = conn.exec(sql_query, MysqlParams(values))?;
    Ok(rows.len() as i64)
}

// Wrapper struct so we can impl From for mysql::Params
struct MysqlParams(sea_query::Values);

impl From<MysqlParams> for mysql::Params {
    fn from(p: MysqlParams) -> Self {
        use mysql::Params;
        use sea_query::Value as SValue;
        use mysql::Value as PValue;

        let values: Vec<PValue> = p.0.iter()
            .map(|f| {
                match f {
                    SValue::Bool(Some(v)) => PValue::from(v),
                    SValue::TinyInt(Some(v)) => PValue::from(v),
                    SValue::SmallInt(Some(v)) => PValue::from(v),
                    SValue::Int(Some(v)) => PValue::from(v),
                    SValue::BigInt(Some(v)) => PValue::from(v),
                    SValue::TinyUnsigned(Some(v)) => PValue::from(v),
                    SValue::SmallUnsigned(Some(v)) => PValue::from(v),
                    SValue::Unsigned(Some(v)) => PValue::from(v),
                    SValue::BigUnsigned(Some(v)) => PValue::from(v),
                    SValue::Float(Some(v)) => PValue::from(v),
                    SValue::Double(Some(v)) => PValue::from(v),
                    SValue::String(Some(v)) => PValue::from(v.deref()),
                    SValue::Bytes(Some(v)) => PValue::from(v.deref()),
                    _ => unreachable!("This is not reachable with default sea_query feature set")
                }
            })
            .collect();

        Params::Positional(values)
    }
}

enum MessagesTable {
    Table,
    Id,
    Subject,
    Sender,
    Receiver,
    Cc,
    Bcc,
    // Body,
    // BodyMime,
    Timestamp
}

// Using manual impl rather than derive macro to save on compile time
impl sea_query::Iden for MessagesTable {
    fn unquoted(&self, s: &mut dyn Write) {
        write!(
            s,
            "{}",
            match self {
                Self::Table => "messages",
                Self::Id => "id",
                Self::Subject => "subject",
                Self::Sender => "sender",
                Self::Receiver => "receiver",
                Self::Cc => "cc",
                Self::Bcc => "bcc",
                // Self::Body => "body",
                // Self::BodyMime => "body_mime",
                Self::Timestamp => "timestamp"
            }
        ).expect("Failed to write");
    }
}

impl From<OrderBy> for MessagesTable {
    fn from(p: OrderBy) -> Self {
        MessagesTable::from(&p)
    }
}

impl From<&OrderBy> for MessagesTable {
    fn from(p: &OrderBy) -> Self {
        match p {
            OrderBy::Subject => Self::Subject,
            OrderBy::To => Self::Receiver,
            OrderBy::From => Self::Sender,
            OrderBy::Cc => Self::Cc,
            OrderBy::Bcc => Self::Bcc,
            OrderBy::Timestamp => Self::Timestamp
        }
    }
}

impl From<Order> for sea_query::Order {
    fn from(p: Order) -> Self {
        sea_query::Order::from(&p)
    }
}

impl From<&Order> for sea_query::Order {
    fn from(p: &Order) -> Self {
        match p {
            Order::Asc => Self::Asc,
            Order::Desc => Self::Desc
        }
    }
}