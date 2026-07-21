package the.waste.fellow.sms.sync

/** Direction of a message relative to this device. */
enum class SyncDirection { INBOUND, OUTBOUND }

/** Minimal transport-agnostic representation of a message to sync to a server. */
data class SyncableSms(
    val address: String,
    val body: String,
    val date: Long,
    val direction: SyncDirection,
)

/**
 * Phase-2 hook: sync messages to a personal server so OTPs / message history are reachable
 * without opening the app. This interface is the seam; there is intentionally NO network
 * implementation yet — [LocalOnlySyncRepository] is the wired-in default no-op.
 *
 * Call sites already exist (see SaveSmsService for inbound, SentSmsWriter for outbound), so
 * enabling sync later means providing a real implementation and swapping [SmsSync.repository]
 * plus scheduling [SyncWorker] — no changes to receivers/services required.
 */
interface SmsSyncRepository {
    fun enqueueInbound(message: SyncableSms)
    fun enqueueOutbound(message: SyncableSms)
    fun pendingCount(): Int
}

/** Default no-op implementation: everything stays on-device. */
object LocalOnlySyncRepository : SmsSyncRepository {
    override fun enqueueInbound(message: SyncableSms) = Unit
    override fun enqueueOutbound(message: SyncableSms) = Unit
    override fun pendingCount(): Int = 0
}

/** Global injection point for the active sync repository. */
object SmsSync {
    @Volatile
    var repository: SmsSyncRepository = LocalOnlySyncRepository
}
