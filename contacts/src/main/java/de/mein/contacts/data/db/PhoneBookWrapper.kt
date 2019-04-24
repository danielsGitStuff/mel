package de.mein.contacts.data.db

import de.mein.auth.data.ServicePayload

class PhoneBookWrapper : ServicePayload() {
    var phoneBook: PhoneBook? = null
}