use std::str::FromStr;
use crate::env::AppData;
use crate::check_scopes;
use crate::error::HttpResult;
use crate::RT;

use actix_web::{web, get, HttpRequest, HttpResponse};
use mysql::prelude::Queryable;
use mysql::{Row, PooledConn};
use serde::Serialize;
use std::sync::Arc;
use anyhow::Result;

pub mod update;

const REQUIRED_SCOPES: [&str; 1] = ["mrfriendly.mailsync.admin"];

#[derive(Serialize)]
struct Response {
    settings: Vec<Setting>
}

#[derive(Serialize)]
pub struct Setting {
    name:       String,
    value:      String,
    rule_type:  RuleType,
}

#[derive(Serialize)]
#[serde(rename_all = "lowercase")]
pub enum RuleType {
    Url,
}

impl FromStr for RuleType {
    type Err = ();
    fn from_str(s: &str) -> std::result::Result<Self, Self::Err> {
        match s {
            "Url" => Ok(Self::Url),
            _ => Err(())
        }
    }
}

#[get("/settings")]
pub async fn settings(data: web::Data<Arc<AppData>>, req: HttpRequest) -> HttpResult {
    let _guard = RT.enter();
    check_scopes!(req, data, REQUIRED_SCOPES);
    let mut conn = data.pool.get_conn()?;

    let settings = get_settings(&mut conn)?;


    Ok(HttpResponse::Ok().json(&Response { settings }))
}

fn get_settings(conn: &mut PooledConn) -> Result<Vec<Setting>> {
    let rows: Vec<Row> = conn.exec("SELECT name,value,rule_type FROM configs", mysql::Params::Empty)?;
    Ok(rows.into_iter()
        .map(|f| {
            Setting {
                name: f.get("name").unwrap(),
                value: f.get("value").unwrap(),
                rule_type: RuleType::from_str(&f.get::<String, &str>("rule_type").unwrap()).expect("rule_type has been improperly serialized")
            }
        })
        .collect())
}