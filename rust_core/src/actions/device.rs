use crate::input::InputController;
use serde::{Deserialize, Serialize};
use ts_rs::TS;

#[derive(Serialize, Deserialize, Debug, TS)]
#[ts(export)]
#[serde(tag = "type")]
pub enum DeviceAction {
    Key { key_code: i32 },
    InputText { text: String },
    Shell { cmd: String },
}

pub fn handle(action: &DeviceAction, controller: &dyn InputController) {
    match action {
        DeviceAction::Key { key_code } => controller.key_event(*key_code),
        DeviceAction::InputText { text } => controller.input_text(text),
        DeviceAction::Shell { cmd } => controller.shell(cmd),
    }
}
