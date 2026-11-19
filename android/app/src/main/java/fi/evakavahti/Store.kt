// Copyright (c) 2026 Tatu Satoniitty. SPDX-License-Identifier: MIT
package fi.evakavahti

import android.app.Application
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private object ProtectedState : Serializer<State> {
    override val defaultValue = State()
    private const val alias = "fi.evakavahti.storage.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES","AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    override suspend fun readFrom(input: InputStream): State {
        val blob = input.readBytes()
        require(blob.size >= 29 && blob[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 1, 12))
        return wire.decodeFromString(cipher.doFinal(blob, 13, blob.size - 13).decodeToString())
    }
    override suspend fun writeTo(t: State, output: OutputStream) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        require(cipher.iv.size == 12)
        output.write(1)
        output.write(cipher.iv)
        output.write(cipher.doFinal(wire.encodeToString(t).encodeToByteArray()))
    }
}
class VahtiApp: Application() {
    val store by lazy { DataStoreFactory.create(ProtectedState) { File(noBackupFilesDir, "vahti.bin") } }
    val repo by lazy { Repository(this) }
}
