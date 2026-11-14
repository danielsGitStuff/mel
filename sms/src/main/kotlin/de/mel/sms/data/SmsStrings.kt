package de.mel.sms.data

object SmsStrings {
    const val TYPE = "sms"
    const val NAME = "SMS"
    const val ROLE_SERVER = "server"
    const val ROLE_CLIENT = "client"

    const val INTENT_REG_AS_CLIENT = "sms_reg_client"
    const val INTENT_PUSH_BATCH = "sms_push_batch"
    const val INTENT_PULL_SINCE = "sms_pull_since"
    const val INTENT_SHARE_SECRET = "sms_share_secret"
    const val INTENT_NEW_MESSAGES = "sms_new_messages"

    const val DB_FILENAME = "sms.db"
    const val SETTINGS_FILENAME = "sms.settings.json"
}
