use crate::types::PlatformCallback;
use serde::{Deserialize, Serialize};
use std::{thread, time};
use ts_rs::TS;

#[derive(Serialize, Deserialize, Debug, TS)]
#[ts(export)]
#[serde(tag = "type")]
pub enum ControlAction {
    Wait { ms: u64 },
    Log { msg: String },
}

pub fn handle(action: &ControlAction, callback: &Box<dyn PlatformCallback>) {
    match action {
        ControlAction::Wait { ms } => {
            thread::sleep(time::Duration::from_millis(*ms));
        }
        ControlAction::Log { msg } => {
            callback.log(format!("{}\n", msg));
        }
    }
}
