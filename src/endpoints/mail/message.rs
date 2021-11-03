use crate::RT;
use crate::error::{Error, HttpResult};
use crate::env::AppData;
use crate::check_scopes;

use actix_web::{web, get, HttpRequest, HttpResponse};
use serde::{Deserialize, Serialize};
use mysql::prelude::Queryable;
use mysql::{Row, params};
use std::sync::Arc;
use log::debug;

const REQUIRED_SCOPES: [&str; 1] = ["mrfriendly.mailsync.mail.message"];

#[derive(Serialize)]
struct ResponseMessage {
    id:         String,
    subject:    Option<String>,
    to:         Option<String>,
    from:       Option<String>,
    cc:         Option<String>,
    bcc:        Option<String>,
    body:       Option<String>,
    body_mime:  Option<String>,
    timestamp:  i64
}

#[derive(Deserialize)]
pub struct QueryParams {
    #[serde(default)]
    format: Format
}

#[derive(Deserialize, Debug)]
pub enum Format {
    Base64,
    Plain
}

impl Default for Format {
    fn default() -> Self {
        Self::Base64
    }
}

#[get("/mail/message/{message_id}")]
pub async fn message(data: web::Data<Arc<AppData>>, req: HttpRequest, query_params: web::Query<QueryParams>, web::Path(message_id): web::Path<String>) -> HttpResult {
    let _guard = RT.enter();

    check_scopes!(req, data, REQUIRED_SCOPES);

    debug!("Creating mysql connection");
    let mut conn = data.pool.get_conn()?;

    debug!("Fetching data");
    let row: Row = match conn.exec_first("SELECT subject,sender,receiver,cc,bcc,body,body_mime,timestamp FROM messages WHERE id = :id", params! {
        "id" => &message_id
    })? {
        Some(r) => r,
        None => return Err(Error::NotFound("The requested message does not exist"))
    };

    debug!("Requested body format is {:?}", query_params.format);
    let body_plain: Option<String> = row.get("body");
    let body = match (body_plain, &query_params.format) {
        (Some(b), Format::Base64) => Some(base64::encode(&b)),
        (Some(b), Format::Plain) => Some(b),
        (None, _) => None
    };

    let message = ResponseMessage {
        id: message_id,
        subject: row.get("subject").unwrap(),
        to: row.get("receiver").unwrap(),
        from: row.get("sender").unwrap(),
        cc: row.get("cc").unwrap(),
        bcc: row.get("bcc").unwrap(),
        body,
        body_mime: row.get("body_mime").unwrap(),
        timestamp: row.get("timestamp").unwrap()
    };

    Ok(HttpResponse::Ok().json(&message))
}