package com.safeharborsecurity.app.data.repository

import com.safeharborsecurity.app.data.local.dao.EmailAccountDao
import com.safeharborsecurity.app.data.local.entity.EmailAccountEntity
import com.safeharborsecurity.app.util.KeystoreManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailAccountRepository @Inject constructor(
    private val emailAccountDao: EmailAccountDao,
    private val keystoreManager: KeystoreManager
) {
    fun getAccounts(): Flow<List<EmailAccountEntity>> = emailAccountDao.getAll()

    suspend fun getActiveAccounts(): List<EmailAccountEntity> = emailAccountDao.getActive()

    suspend fun getAccountCount(): Int = emailAccountDao.getCount()

    suspend fun addAccount(email: String, displayName: String, provider: String, authToken: String = "") {
        val encrypted = if (authToken.isNotBlank()) keystoreManager.encrypt(authToken) else ""
        emailAccountDao.upsert(
            EmailAccountEntity(
                emailAddress = email,
                displayName = displayName,
                provider = provider,
                authTokenEncrypted = encrypted
            )
        )
    }

    suspend fun removeAccount(email: String) {
        emailAccountDao.delete(email)
    }

    suspend fun updateSyncStats(email: String, scanned: Int, threats: Int) {
        emailAccountDao.updateSyncStats(email, System.currentTimeMillis(), scanned, threats)
    }

    suspend fun getDecryptedToken(account: EmailAccountEntity): String {
        return if (account.authTokenEncrypted.isNotBlank()) {
            keystoreManager.decrypt(account.authTokenEncrypted)
        } else ""
    }
}
