package de.mel.filesync.bash

import de.mel.auth.file.StandardFile

interface AutoKlausIterator<out T> : Iterator<T?>, AutoCloseable {
    class EmpyAutoKlausIterator<T> : AutoKlausIterator<T> {
        @Throws(Exception::class)
        override fun close() {
        }

        override fun hasNext(): Boolean {
            return false
        }

        override fun next(): T? {
            return null
        }
    }

    class EmptyAutoKlausIterator<out O> : AutoKlausIterator<O>{
        override fun hasNext(): Boolean  = false

        override fun next(): O? = null

        override fun close() {
        }
    }
}