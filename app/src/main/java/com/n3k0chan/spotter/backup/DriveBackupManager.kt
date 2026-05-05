package com.n3k0chan.spotter.backup

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.n3k0chan.spotter.data.db.SpotterDatabase
import com.n3k0chan.spotter.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

/**
 * Sube/descarga la base SQLite a la carpeta especial "appDataFolder" de Google Drive.
 * appDataFolder es invisible para el usuario en su Drive (drive.google.com) y solo accesible
 * por nuestra app autenticada con el mismo OAuth client (package + SHA-1).
 *
 * Requiere registrar en Google Cloud Console un OAuth 2.0 client de tipo Android con:
 *   - Package name: com.n3k0chan.spotter
 *   - SHA-1 de la firma de release y/o debug
 *
 * No requiere Web Client ID porque usamos GoogleAccountCredential (no Credentials Manager).
 */
class DriveBackupManager(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    companion object {
        private const val DB_FILENAME = "spotter.db"
        private const val APP_DATA_SPACE = "appDataFolder"
        private const val MIME_SQLITE = "application/x-sqlite3"
    }

    private val dbDir get() = context.getDatabasePath(DB_FILENAME).parentFile!!
    private val dbFile get() = context.getDatabasePath(DB_FILENAME)

    /** Crea credenciales OAuth para la cuenta seleccionada. Necesita network en background. */
    private fun credentialFor(accountName: String): GoogleAccountCredential {
        val cred = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA),
        )
        cred.selectedAccount = Account(accountName, "com.google")
        return cred
    }

    private fun driveFor(accountName: String): Drive =
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credentialFor(accountName),
        ).setApplicationName("Spotter").build()

    val isLinked: Boolean get() = !settings.state.value.driveAccountName.isNullOrBlank()

    suspend fun backupNow(): BackupResult = withContext(Dispatchers.IO) {
        val account = settings.state.value.driveAccountName
            ?: return@withContext BackupResult.NotLinked
        try {
            // Forzar checkpoint del WAL para que spotter.db tenga todos los datos
            SpotterDatabase.get(context).query("PRAGMA wal_checkpoint(FULL)", null).use { /* drain */ }

            if (!dbFile.exists()) return@withContext BackupResult.Error(IllegalStateException("Sin BD local"))

            val drive = driveFor(account)
            val existingId = findExistingFileId(drive)
            val media = FileContent(MIME_SQLITE, dbFile)
            val metadata = DriveFile().apply {
                name = DB_FILENAME
                mimeType = MIME_SQLITE
                if (existingId == null) parents = listOf(APP_DATA_SPACE)
            }

            val updated = if (existingId == null) {
                drive.files().create(metadata, media)
                    .setFields("id, modifiedTime, size")
                    .execute()
            } else {
                drive.files().update(existingId, DriveFile().apply { name = DB_FILENAME }, media)
                    .setFields("id, modifiedTime, size")
                    .execute()
            }

            val ts = updated.modifiedTime?.value ?: System.currentTimeMillis()
            settings.setLastBackupAt(ts)
            BackupResult.Ok(ts)
        } catch (e: UserRecoverableAuthIOException) {
            BackupResult.NeedsConsent(e.intent)
        } catch (e: Exception) {
            BackupResult.Error(e)
        }
    }

    suspend fun restoreNow(): RestoreResult = withContext(Dispatchers.IO) {
        val account = settings.state.value.driveAccountName
            ?: return@withContext RestoreResult.NotLinked
        try {
            val drive = driveFor(account)
            val fileId = findExistingFileId(drive)
                ?: return@withContext RestoreResult.NoBackupYet

            // Descarga a un .new dentro del directorio databases
            val temp = java.io.File(dbDir, "$DB_FILENAME.restore")
            FileOutputStream(temp).use { out ->
                drive.files().get(fileId).executeMediaAndDownloadTo(out)
            }

            // Cierra la base y borra los archivos actuales (incluido WAL/SHM)
            SpotterDatabase.closeAndClear()
            listOf(DB_FILENAME, "$DB_FILENAME-shm", "$DB_FILENAME-wal").forEach {
                java.io.File(dbDir, it).delete()
            }

            // Reemplaza
            if (!temp.renameTo(dbFile)) {
                temp.copyTo(dbFile, overwrite = true)
                temp.delete()
            }
            RestoreResult.Ok
        } catch (e: UserRecoverableAuthIOException) {
            RestoreResult.NeedsConsent(e.intent)
        } catch (e: Exception) {
            RestoreResult.Error(e)
        }
    }

    private fun findExistingFileId(drive: Drive): String? {
        val list = drive.files().list()
            .setSpaces(APP_DATA_SPACE)
            .setQ("name = '$DB_FILENAME'")
            .setFields("files(id, modifiedTime)")
            .setPageSize(10)
            .execute()
        return list.files?.firstOrNull()?.id
    }

    sealed class BackupResult {
        data class Ok(val timestamp: Long) : BackupResult()
        data object NotLinked : BackupResult()
        data class NeedsConsent(val resolutionIntent: Intent) : BackupResult()
        data class Error(val cause: Throwable) : BackupResult()
    }

    sealed class RestoreResult {
        data object Ok : RestoreResult()
        data object NotLinked : RestoreResult()
        data object NoBackupYet : RestoreResult()
        data class NeedsConsent(val resolutionIntent: Intent) : RestoreResult()
        data class Error(val cause: Throwable) : RestoreResult()
    }
}
