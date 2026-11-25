package com.example.data

import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.model.AiProject
import com.example.model.AuthEvent
import com.example.model.AuthEventType
import com.example.model.BugCategory
import com.example.model.BugReport
import com.example.model.BugStatus
import com.example.model.ChatMessage
import com.example.model.ChatThread
import com.example.model.SavedArtifact
import com.example.model.SessionStatus
import com.example.model.StylePreset
import com.example.model.UserApp
import com.example.model.UserProfile
import com.example.model.ZenvixModelOption
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ZenvixFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing cloud data.")
    }

    fun observeChats(userId: String): Flow<List<ChatThread>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/chats"
        emitAll(
            db.collection("users").document(userId).collection("chats")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatThread::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(userId: String, chatId: String): Flow<List<ChatMessage>> = flow {
        require(userId.isNotBlank() && chatId.isNotBlank()) { "User ID and Chat ID must not be blank" }
        val path = "users/$userId/chats/$chatId/messages"
        emitAll(
            db.collection("users").document(userId)
                .collection("chats").document(chatId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProjects(userId: String): Flow<List<AiProject>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AiProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeArtifacts(userId: String): Flow<List<SavedArtifact>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/artifacts"
        emitAll(
            db.collection("users").document(userId).collection("artifacts")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedArtifact::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserApps(userId: String): Flow<List<UserApp>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/userApps"
        emitAll(
            db.collection("users").document(userId).collection("userApps")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserApp::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createChat(
        userId: String,
        title: String,
        projectId: String?,
        modelId: String,
        stylePreset: StylePreset,
        thinkingEnabled: Boolean,
        firstPreview: String
    ): Result<String> {
        var docPath = "users/$userId/chats"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val docRef = db.collection("users").document(uid).collection("chats").document()
            docPath = docRef.path
            val sanitizedTitle = title.trim().ifBlank { "New conversation" }.take(120)
            val sanitizedPreview = firstPreview.trim().take(240)
            val sanitizedModelId = modelId.trim().ifBlank { ZenvixModelOption.SONNET_FLASH.modelId }.take(80)
            val payload = mapOf(
                "userId" to uid,
                "title" to sanitizedTitle,
                "projectId" to projectId?.takeIf { it.isNotBlank() },
                "modelId" to sanitizedModelId,
                "stylePreset" to stylePreset.name,
                "thinkingEnabled" to thinkingEnabled,
                "pinned" to false,
                "lastMessagePreview" to sanitizedPreview,
                "messageCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateChatMetadata(
        userId: String,
        chatId: String,
        title: String? = null,
        modelId: String? = null,
        stylePreset: StylePreset? = null,
        thinkingEnabled: Boolean? = null,
        pinned: Boolean? = null,
        lastMessagePreview: String? = null,
        messageCount: Long? = null
    ): Result<Unit> {
        var docPath = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(chatId.isNotBlank()) { "Chat ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("chats").document(chatId)
            docPath = docRef.path
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            title?.let { updates["title"] = it.trim().ifBlank { "New conversation" }.take(120) }
            modelId?.let { updates["modelId"] = it.trim().ifBlank { ZenvixModelOption.SONNET_FLASH.modelId }.take(80) }
            stylePreset?.let { updates["stylePreset"] = it.name }
            thinkingEnabled?.let { updates["thinkingEnabled"] = it }
            pinned?.let { updates["pinned"] = it }
            lastMessagePreview?.let { updates["lastMessagePreview"] = it.trim().take(240) }
            messageCount?.let { updates["messageCount"] = it.coerceIn(0L, 10000L) }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteChat(userId: String, chatId: String): Result<Unit> {
        val docPath = "users/$userId/chats/$chatId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(chatId.isNotBlank()) { "Chat ID must not be blank" }
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatId)
            val msgSnapshots = chatRef.collection("messages").limit(500).get().await()
            if (!msgSnapshots.isEmpty) {
                val batch = db.batch()
                msgSnapshots.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            chatRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        userId: String,
        chatId: String,
        role: String,
        content: String,
        thinkingSummary: String? = null,
        attachmentName: String? = null,
        artifactTitle: String? = null,
        artifactLanguage: String? = null,
        artifactContent: String? = null,
        modelId: String? = null
    ): Result<String> {
        var docPath = "users/$userId/chats/$chatId/messages"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            require(chatId.isNotBlank()) { "Chat ID must not be blank" }
            val msgRef = db.collection("users").document(uid)
                .collection("chats").document(chatId)
                .collection("messages").document()
            docPath = msgRef.path
            val payload = mapOf(
                "userId" to uid,
                "chatId" to chatId,
                "role" to role,
                "content" to content.take(100000),
                "thinkingSummary" to thinkingSummary?.takeIf { it.isNotBlank() }?.take(20000),
                "attachmentName" to attachmentName?.takeIf { it.isNotBlank() }?.take(160),
                "artifactTitle" to artifactTitle?.takeIf { it.isNotBlank() }?.take(120),
                "artifactLanguage" to artifactLanguage?.takeIf { it.isNotBlank() }?.take(40),
                "artifactContent" to artifactContent?.takeIf { it.isNotBlank() }?.take(100000),
                "modelId" to modelId?.takeIf { it.isNotBlank() }?.take(60),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            msgRef.set(payload).await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveProject(
        userId: String,
        existingProjectId: String?,
        name: String,
        description: String,
        systemPrompt: String,
        stylePreset: StylePreset,
        accentIndex: Long
    ): Result<String> {
        val isNew = existingProjectId.isNullOrBlank()
        var docPath = "users/$userId/projects"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val projectsCol = db.collection("users").document(uid).collection("projects")
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Project name cannot be empty" }
            val cleanDesc = description.trim().take(240)
            val cleanPrompt = systemPrompt.trim().take(4000)
            val clampedAccent = accentIndex.coerceIn(0L, 5L)

            if (isNew) {
                val docRef = projectsCol.document()
                docPath = docRef.path
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to cleanName,
                        "description" to cleanDesc,
                        "systemPrompt" to cleanPrompt,
                        "stylePreset" to stylePreset.name,
                        "accentIndex" to clampedAccent,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(docRef.id)
            } else {
                val id = existingProjectId
                val docRef = projectsCol.document(id)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "description" to cleanDesc,
                        "systemPrompt" to cleanPrompt,
                        "stylePreset" to stylePreset.name,
                        "accentIndex" to clampedAccent,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(userId: String, projectId: String): Result<Unit> {
        val docPath = "users/$userId/projects/$projectId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            db.collection("users").document(uid).collection("projects").document(projectId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveArtifact(
        userId: String,
        title: String,
        language: String,
        content: String,
        chatId: String?
    ): Result<String> {
        var docPath = "users/$userId/artifacts"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanTitle = title.trim().ifBlank { "Untitled Artifact" }.take(120)
            val cleanLang = language.trim().ifBlank { "text" }.take(40)
            val cleanContent = content.take(100000)
            require(cleanContent.isNotEmpty()) { "Artifact content cannot be empty" }
            val docRef = db.collection("users").document(uid).collection("artifacts").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "language" to cleanLang,
                "content" to cleanContent,
                "chatId" to chatId?.takeIf { it.isNotBlank() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteArtifact(userId: String, artifactId: String): Result<Unit> {
        val docPath = "users/$userId/artifacts/$artifactId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            db.collection("users").document(uid).collection("artifacts").document(artifactId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveUserApp(
        userId: String,
        existingAppId: String?,
        name: String,
        description: String,
        htmlCode: String,
        lastPrompt: String,
        modelId: String,
        version: Long,
        accentIndex: Long
    ): Result<String> {
        val isNew = existingAppId.isNullOrBlank()
        var docPath = "users/$userId/userApps"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val appsCol = db.collection("users").document(uid).collection("userApps")
            val cleanName = name.trim().ifBlank { "My Zenvix App" }.take(80)
            val cleanDesc = description.trim().take(300)
            val cleanCode = htmlCode.take(150000)
            require(cleanCode.isNotBlank()) { "App HTML source cannot be empty" }
            val cleanPrompt = lastPrompt.trim().take(1000)
            val cleanModel = modelId.trim().ifBlank { ZenvixModelOption.SONNET_FLASH.modelId }.take(80)
            val clampedVersion = version.coerceIn(1L, 10000L)
            val clampedAccent = accentIndex.coerceIn(0L, 5L)

            if (isNew) {
                val docRef = appsCol.document()
                docPath = docRef.path
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to cleanName,
                        "description" to cleanDesc,
                        "htmlCode" to cleanCode,
                        "lastPrompt" to cleanPrompt,
                        "modelId" to cleanModel,
                        "version" to clampedVersion,
                        "accentIndex" to clampedAccent,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(docRef.id)
            } else {
                val id = existingAppId
                val docRef = appsCol.document(id)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "description" to cleanDesc,
                        "htmlCode" to cleanCode,
                        "lastPrompt" to cleanPrompt,
                        "modelId" to cleanModel,
                        "version" to clampedVersion,
                        "accentIndex" to clampedAccent,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteUserApp(userId: String, appId: String): Result<Unit> {
        val docPath = "users/$userId/userApps/$appId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            db.collection("users").document(uid).collection("userApps").document(appId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeMyBugReports(userId: String): Flow<List<BugReport>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "bugReports"
        emitAll(
            db.collection("bugReports")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BugReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllBugReportsForOwner(): Flow<List<BugReport>> = flow {
        val uid = requireUserId()
        val path = "bugReports"
        emitAll(
            db.collection("bugReports")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BugReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun submitBugReport(
        userId: String,
        reporterName: String,
        reporterEmail: String,
        category: BugCategory,
        title: String,
        description: String,
        diagnostics: String
    ): Result<String> {
        var docPath = "bugReports"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanTitle = title.trim().take(120)
            val cleanDesc = description.trim().take(4000)
            require(cleanTitle.isNotEmpty() && cleanDesc.isNotEmpty()) {
                "Bug title and description cannot be empty."
            }
            val cleanName = reporterName.trim().ifBlank { "Zenvix User" }.take(80)
            val cleanEmail = reporterEmail.trim().take(160)
            val cleanDiagnostics = diagnostics.trim().take(2000)

            val docRef = db.collection("bugReports").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "reporterName" to cleanName,
                    "reporterEmail" to cleanEmail,
                    "category" to category.name,
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "diagnostics" to cleanDiagnostics,
                    "status" to BugStatus.OPEN.name,
                    "ownerNote" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateBugReportStatusForOwner(
        reportId: String,
        status: BugStatus,
        ownerNote: String
    ): Result<Unit> {
        val docPath = "bugReports/$reportId"
        return try {
            requireUserId()
            require(reportId.isNotBlank()) { "Report ID must not be blank" }
            db.collection("bugReports").document(reportId)
                .update(
                    mapOf(
                        "status" to status.name,
                        "ownerNote" to ownerNote.trim().take(1000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBugReport(reportId: String): Result<Unit> {
        val docPath = "bugReports/$reportId"
        return try {
            requireUserId()
            require(reportId.isNotBlank()) { "Report ID must not be blank" }
            db.collection("bugReports").document(reportId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertUserProfile(
        userId: String,
        displayName: String,
        email: String,
        photoUrl: String,
        lastModelId: String,
        chatCount: Long,
        appCount: Long,
        sessionStatus: SessionStatus = SessionStatus.SIGNED_IN
    ): Result<Unit> {
        val docPath = "userProfiles/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val docRef = db.collection("userProfiles").document(uid)
            val cleanName = displayName.trim().ifBlank { "Zenvix User" }.take(80)
            val cleanEmail = email.trim().take(160)
            val cleanPhoto = photoUrl.trim().take(500)
            val cleanModel = lastModelId.trim().ifBlank { ZenvixModelOption.SONNET_FLASH.modelId }.take(80)
            val clampedChats = chatCount.coerceIn(0L, 100000L)
            val clampedApps = appCount.coerceIn(0L, 100000L)

            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to cleanName,
                        "email" to cleanEmail,
                        "photoUrl" to cleanPhoto,
                        "lastModelId" to cleanModel,
                        "chatCount" to clampedChats,
                        "appCount" to clampedApps,
                        "sessionStatus" to sessionStatus.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to cleanName,
                        "email" to cleanEmail,
                        "photoUrl" to cleanPhoto,
                        "lastModelId" to cleanModel,
                        "chatCount" to clampedChats,
                        "appCount" to clampedApps,
                        "sessionStatus" to sessionStatus.name,
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeAllUserProfilesForOwner(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "userProfiles"
        emitAll(
            db.collection("userProfiles")
                .orderBy("lastActiveAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun recordAuthEvent(
        userId: String,
        displayName: String,
        email: String,
        eventType: AuthEventType,
        deviceInfo: String
    ): Result<Unit> {
        var docPath = "authEvents"
        return try {
            val currentAuthUser = auth.currentUser
                ?: throw IllegalStateException("User must be signed in to record auth activity.")
            val uid = currentAuthUser.uid
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanName = displayName.trim().ifBlank { "Zenvix User" }.take(80)
            val cleanEmail = (currentAuthUser.email ?: email).trim().take(160)
            val cleanDevice = deviceInfo.trim().take(240)
            val docRef = db.collection("authEvents").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "email" to cleanEmail,
                    "eventType" to eventType.name,
                    "deviceInfo" to cleanDevice,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    fun observeAuthEventsForOwner(): Flow<List<AuthEvent>> = flow {
        val uid = requireUserId()
        val path = "authEvents"
        emitAll(
            db.collection("authEvents")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(200)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AuthEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun deleteAuthEventForOwner(eventId: String): Result<Unit> {
        val docPath = "authEvents/$eventId"
        return try {
            requireUserId()
            require(eventId.isNotBlank()) { "Event ID must not be blank" }
            db.collection("authEvents").document(eventId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
