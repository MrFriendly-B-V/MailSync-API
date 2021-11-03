use crate::env::AppData;
use crate::check_scopes;
use crate::error::HttpResult;
use crate::RT;

use actix_web::{web, post, HttpRequest, HttpResponse};
use mysql::{PooledConn, params};
use mysql::prelude::Queryable;
use serde::Deserialize;
use std::sync::Arc;
use anyhow::Result;

#[derive(Deserialize)]
pub struct Request {
    settings: Vec<Setting>
}

#[derive(Deserialize)]
pub struct Setting {
    name:       String,
    value:      String,
}

const REQUIRED_SCOPES: [&str; 1] = ["mrfriendly.mailsync.admin"];

#[post("/settings/update")]
pub async fn update(data: web::Data<Arc<AppData>>, req: HttpRequest, payload: web::Json<Request>) -> HttpResult {
    let _guard = RT.enter();
    check_scopes!(req, data, REQUIRED_SCOPES);
    let mut conn = data.pool.get_conn()?;

    for setting in &payload.settings {
        update_settings(&mut conn, setting)?;
    }

    Ok(HttpResponse::Ok().finish())
}

fn update_settings(conn: &mut PooledConn, setting: &Setting) -> Result<()> {
    conn.exec_drop("UPDATE configs SET value = :value WHERE name = :name", params! {
        "name" => &setting.name,
        "value" => &setting.value
    })?;

    Ok(())
}