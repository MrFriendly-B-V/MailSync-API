#[macro_use]
extern crate lazy_static;

mod env;
mod threads;
mod endpoints;
mod error;
mod api;

use std::process::exit;
use std::sync::Arc;
use actix_governor::GovernorConfigBuilder;
use actix_web::{App, HttpServer};
use actix_web::middleware::normalize::TrailingSlash;
use log::{error, info, debug};

lazy_static! {
    pub static ref RT: tokio::runtime::Runtime = tokio::runtime::Runtime::new().expect("Failed to create Tokio runtime");
    pub static ref RQ: reqwest::Client = reqwest::Client::new();
}

#[actix_web::main]
pub async fn main() -> std::io::Result<()> {
    log4rs::init_file("./log4rs.yaml", Default::default()).expect("Failed to initialize logger");
    info!("Starting {} v{}", env!("CARGO_PKG_NAME"), env!("CARGO_PKG_VERSION"));

    debug!("Reading environment");
    let env: env::Env = match envy::from_env() {
        Ok(e) => e,
        Err(e) => {
            error!("Failed to read environment: {:?}", e);
            exit(1);
        }
    };

    debug!("Creating appdata object");
    let appdata = match env::AppData::new(&env) {
        Ok(a) => a,
        Err(e) => {
            error!("Failed to create AppData object: {:?}", e);
            exit(1);
        }
    };

    info!("Applying migrations");
    match appdata.migrate() {
        Ok(_) => {},
        Err(e) => {
            error!("Failed to apply migrations: {:?}", e);
            exit(1);
        }
    }
    let appdata_arc = Arc::new(appdata);

    debug!("Starting inbox thread");
    threads::inbox::query_inbox(appdata_arc.clone());

    debug!("Creating Governor config");
    let gov_conf = GovernorConfigBuilder::default()
        .per_second(2)
        .burst_size(5)
        .finish()
        .unwrap(); // Save because the config is hardcoded

    debug!("Starting actix server");
    HttpServer::new(move || {
        App::new()
            .wrap(actix_web::middleware::Logger::default())
            .wrap(actix_web::middleware::NormalizePath::new(TrailingSlash::Trim))
            .wrap(actix_cors::Cors::permissive())
            .wrap(actix_governor::Governor::new(&gov_conf))
            .data(appdata_arc.clone())
            .service(endpoints::user::add::add)
            .service(endpoints::mail::inbox::inbox)
            .service(endpoints::mail::message::message)
    }).bind("[::]:8080")?.run().await
}
