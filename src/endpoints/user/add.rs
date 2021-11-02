use std::sync::Arc;
use actix_web::{web, post, HttpResponse, HttpRequest};
use serde::Deserialize;
use mysql::prelude::Queryable;
use mysql::{Row, params};
use crate::env::AppData;
use crate::RT;
use crate::error::{Error, HttpResult};
use log::debug;

#[derive(Deserialize)]
pub struct Query {
    user_id:    String
}

const REQUIRED_SCOPES: [&str; 1] = ["mrfriendly.mailsync.user.add"];

#[post("/user/add")]
pub async fn add(data: web::Data<Arc<AppData>>, query: web::Query<Query>, req: HttpRequest) -> HttpResult {
    let _guard = RT.enter();
    crate::check_scopes!(req, data, REQUIRED_SCOPES);

    debug!("Verifying user {} exists on Authlander '{}'", &query.user_id, &data.env.authlander_host);
    match authlander_client::User::new(&query.user_id, &data.env.authlander_host).get_scopes().await {
        Ok(_) => {},
        Err(_) => return Err(Error::NotFound("The user does not exist on Authlander"))
    }

    debug!("Checking if user {} already exists in database", &query.user_id);
    let mut conn = data.pool.get_conn()?;
    let exists: Option<Row> = conn.exec_first("SELECT 1 FROM users WHERE id = :id", params! {
        "id" => &query.user_id
    })?;

    if exists.is_some() {
        return Err(Error::Conflict("The user already exists"));
    }

    debug!("Adding user {}", &query.user_id);
    conn.exec_drop("INSERT INTO users (id) VALUES (:id)", params! {
        "id" => &query.user_id
    })?;

    Ok(HttpResponse::Ok().finish())
}