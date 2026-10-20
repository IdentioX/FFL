package ru.ffl24.direct.config

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import ru.ffl24.direct.data.mysql.DirectMySqlSettings

/** All fields encrypted as one authenticated record; no plaintext password or exported key. */
class ConnectionProfileStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "direct-profile-v1.enc"))
    fun exists() = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("ffl_direct_profile_v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("ffl_direct_profile_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): DirectMySqlSettings? {
        if (!exists()) return null
        val bytes = file.readFully()
        require(bytes.size in 29..100_000 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1,13)))
        cipher.updateAAD("ffl-direct-profile-v1".toByteArray())
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(13,bytes.size)), Charsets.UTF_8))
        return DirectMySqlSettings(json.getString("host"),json.getString("port"),json.getString("database"),json.getString("user"),json.getString("password"),"true",json.getString("ca"))
    }
    @Synchronized fun save(settings: DirectMySqlSettings) {
        val json = JSONObject().put("host",settings.host).put("port",settings.port).put("database",settings.database)
            .put("user",settings.user).put("password",settings.password).put("ca",settings.caPem)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key()); cipher.updateAAD("ffl-direct-profile-v1".toByteArray())
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) } catch (e: Exception) { file.failWrite(output); throw e }
    }
}
