package dev.valvassori.rinha.database

import dev.valvassori.rinha.database.dao.PersonDAOImpl
import dev.valvassori.rinha.datasource.PersonDAO
import dev.valvassori.rinha.errors.UnprocessableEntityException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.concurrent.Executors

class DBConnection(private val db: Database) {
    companion object {
        val shared: DBConnection by lazy {
            DBConnection(Database.connect(DatabaseConnectionFactory.newDataSource()))
        }
    }

    val personDAO: PersonDAO by lazy { PersonDAOImpl(this) }

    private val coroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val semaphore = Semaphore(32)

    suspend fun <T> dbQuery(block: suspend () -> T): T = semaphore.withPermit {
        try {
            newSuspendedTransaction(coroutineDispatcher, db) { block() }
        } catch (e: ExposedSQLException) {
            when (e.sqlState) {
                DBErrorCodes.IntegrityViolation.UNIQUE_VIOLATION ->
                    throw UnprocessableEntityException(e.message ?: "Unique key violation")

                else -> throw e
            }
        }
    }
}