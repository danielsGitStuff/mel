package de.mein.contacts.data.db

import de.mein.auth.data.ServicePayload

class PhoneBookWrapper(var phoneBook: PhoneBook?) : ServicePayload() {
    constructor() : this(null)
}