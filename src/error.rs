use actix_web::http::StatusCode;
use actix_web::{HttpResponse, ResponseError};
use serde::Serialize;
use thiserror::Error;
use log::warn;

pub type HttpResult = Result<HttpResponse, Error>;

#[derive(Serialize)]
struct ErrorResponse {
    code:       u16,
    message:    String
}

impl From<&Error> for ErrorResponse {
    fn from(e: &Error) -> Self {
        Self {
            code: e.status_code().as_u16(),
            message: format!("{}", e)
        }
    }
}

#[derive(Debug, Error)]
pub enum Error {
    #[error("Internal Server Error")]
    Mysql(#[from] mysql::Error),
    #[error("Internal Server Error")]
    Gmail,
    #[error("Internal Server Error")]
    Anyhow(#[from] anyhow::Error),
    #[error("Internal Server Error")]
    Reqwest(#[from] reqwest::Error),
    #[error("The requested resource was not found: {0}")]
    NotFound(&'static str),
    #[error("The user did not provide an authorization token, their session has expired, or is not authorized to access the requested resource")]
    Unauthorized,
    #[error("{0}")]
    Conflict(&'static str)
}

impl Error {
    fn log(&self) {
        match self {
            Self::Mysql(e) => warn!("{:?}", e),
            Self::Anyhow(e) => warn!("{:?}", e),
            _ => {}
        }
    }
}

impl ResponseError for Error {
    fn status_code(&self) -> StatusCode {
        match self {
            Self::Mysql(_)  | Self::Anyhow(_) | Self::Reqwest(_) | Self::Gmail => StatusCode::INTERNAL_SERVER_ERROR,
            Self::NotFound(_) => StatusCode::NOT_FOUND,
            Self::Unauthorized => StatusCode::UNAUTHORIZED,
            Self::Conflict(_) => StatusCode::CONFLICT,
        }
    }

    fn error_response(&self) -> HttpResponse {
        self.log();
        let er = ErrorResponse::from(self);
        HttpResponse::build(self.status_code()).json(&er)
    }
}