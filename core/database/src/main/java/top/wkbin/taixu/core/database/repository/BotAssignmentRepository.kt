package top.wkbin.taixu.core.database.repository

import kotlinx.coroutines.flow.Flow
import top.wkbin.taixu.core.model.bot.BotAssignment
import top.wkbin.taixu.core.model.bot.BotAssignmentStatus
import top.wkbin.taixu.core.model.bot.BotWorkBatch

interface BotAssignmentRepository : IndependentAssignmentRepository {
    fun observeForGroup(groupId: String): Flow<List<BotAssignment>>
    suspend fun listRunning(groupId: String): List<BotAssignment>
    suspend fun listRecent(limit: Int = 20): List<BotAssignment>
    suspend fun get(id: String): BotAssignment?
    suspend fun save(assignment: BotAssignment)
    suspend fun finish(
        id: String,
        status: BotAssignmentStatus,
        summary: String? = null,
        error: String? = null,
        finishedAt: Long = System.currentTimeMillis(),
    )

    /** Clean up RUNNING records to failed after restart to avoid infinite executing status. */
    suspend fun failRunning(now: Long = System.currentTimeMillis(), error: String = "Process exited, original results pending verification. Please check session artifacts.")

    suspend fun getBatch(id: String): BotWorkBatch?

    suspend fun listBatches(groupId: String, limit: Int = 20): List<BotWorkBatch>

    suspend fun batchAssignments(batchId: String): List<BotAssignment>

    suspend fun batchTaskCount(batchId: String): Int

    /** 收敛没有活跃子任务、且主控回合已结束的批次。 */
    suspend fun reconcileWaitingBatches(now: Long = System.currentTimeMillis())
}
