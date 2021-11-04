use crate::env::AppData;
use crate::RT;
use crate::error::{Error, HttpResult};

use actix_web::{web, post, HttpResponse, HttpRequest};
use mysql::prelude::Queryable;
use mysql::{Row, params, PooledConn, TxOpts};
use serde::Deserialize;
use std::sync::Arc;

#[derive(Deserialize)]
pub struct Query {
    user_id:    String
}

const REQUIRED_SCOPES: [&str; 1] = ["mrfriendly.mailsync.admin"];

#[post("/user/remove")]
pub async fn remove(data: web::Data<Arc<AppData>>, query: web::Query<Query>, req: HttpRequest) -> HttpResult {
    let _guard = RT.enter();
    crate::check_scopes!(req, data, REQUIRED_SCOPES);
    let mut conn = data.pool.get_conn()?;

    remove_user(&mut conn, &query.user_id)?;
    remove_messages(&mut conn, &query.user_id)?;

    Ok(HttpResponse::Ok().finish())
}

fn remove_user<S: AsRef<str>>(conn: &mut PooledConn, id: S) -> Result<(), Error> {
    conn.exec_drop("DELETE FROM users WHERE id = :id", params! {
        "id" => id.as_ref()
    })?;

    Ok(())
}

fn remove_messages<S: AsRef<str>>(conn: &mut PooledConn, id: S) -> Result<(), Error> {
    let mut trans = conn.start_transaction(TxOpts::default())?;

    // Fetch all message IDs
    let rows: Vec<Row> = trans.exec("SELECT id FROM messages_processed WHERE user_id = :user_id", params! {
        "user_id" => id.as_ref()
    })?;

    for row in rows {
        let id: String = row.get("id").unwrap();

        trans.exec_drop("DELETE FROM messages WHERE id = :id", params! {
            "id" => &id
        })?;

        trans.exec_drop("DELETE FROM messages_processed WHERE id = :id", params! {
            "id" => &id
        })?;
    }

    trans.commit()?;

    Ok(())
}