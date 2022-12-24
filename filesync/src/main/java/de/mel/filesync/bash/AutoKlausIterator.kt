package de.mel.filesync.bash

interface AutoKlausIterator<out T> : Iterator<T>, AutoCloseable {
    class EmpyAutoKlausIterator<T> : AutoKlausIterator<T> {
        @Throws(Exception::class)
        override fun close() {
        }

        override fun hasNext(): Boolean {
            return false
        }

        override fun next(): T = throw IllegalAccessException("no element left")
    }

    class EmptyAutoKlausIterator<out O> : AutoKlausIterator<O>{
        override fun hasNext(): Boolean  = false

        override fun next(): O = throw IllegalAccessException("no element left")

        override fun close() {
        }
    }
}