use crate::env::{AppData, Env};
use crate::{RT, RQ};
use crate::error::{Error, HttpResult};

use actix_web::{web, get, HttpResponse, HttpRequest};
use mysql::{Row, params, PooledConn, Params};
use mysql::prelude::Queryable;
use serde::{Serialize, Deserialize};
use std::sync::Arc;

const REQUIRED_SCOPES: [&str; 1] = ["mrfriendly.mailsync.admin"];

#[derive(Serialize)]
struct Response {
    users:  Vec<SendUser>
}

#[derive(Serialize)]
struct SendUser {
    id:     String,
    name:   String,
    email:  String,
    active: bool,
}

#[get("/user/list")]
pub async fn list(data: web::Data<Arc<AppData>>, req: HttpRequest) -> HttpResult {
    let _guard = RT.enter();
    crate::check_scopes!(req, data, REQUIRED_SCOPES);
    let mut conn = data.pool.get_conn()?;

    let users = list_users(&data.env).await?;
    let users: Result<Vec<_>, Error> = users.into_iter()
        .map(|u| {
            Ok(SendUser {
                active: is_active(&mut conn, &u.id)?,
                id: u.id,
                name: u.name,
                email: u.email,
            })
        })
        .collect();

    let response = Response { users: users? };
    Ok(HttpResponse::Ok().json(&response))
}

#[derive(Deserialize)]
struct ListUserResponse {
    users:  Vec<ReceivedUser>
}

#[derive(Deserialize)]
struct ReceivedUser {
    id:     String,
    name:   String,
    email:  String
}

async fn list_users(env: &Env) -> Result<Vec<ReceivedUser>, Error> {
    let response: ListUserResponse = RQ.get(format!("{}/user/list", env.authlander_host))
        .header("Authorization", &env.authlander_key)
        .send()
        .await?
        .json()
        .await?;

    Ok(response.users)
}

fn is_active<S: AsRef<str>>(conn: &mut PooledConn, id: S) -> Result<bool, Error> {
    match conn.exec_first::<Row, &str, Params>("SELECT id FROM users WHERE id = :id", params! {
        "id" => id.as_ref()
    })? {
        Some(_) => Ok(true),
        None => Ok(false)
    }
}