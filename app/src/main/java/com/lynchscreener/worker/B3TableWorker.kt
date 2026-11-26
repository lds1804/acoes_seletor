package com.lynchscreener.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lynchscreener.data.remote.FundamentusClient
import com.lynchscreener.data.remote.parser.FundamentusParser
import com.lynchscreener.data.repository.AssetRepository
import java.io.IOException

/**
 * Fast queue: one GET on resultado.php, parse the full B3 table, filter out
 * illiquid papers and upsert. Runs periodically (daily) + as a manual kick.
 */
class B3TableWorker(
    appContext: Context,
    params: WorkerParameters,
    private val client: FundamentusClient,
    private val repository: AssetRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        SyncEventLog.log("B3: buscando tabela geral do Fundamentus")
        val html = client.fetchResultTable()
        val rows = FundamentusParser.parseResultTable(html)
        SyncEventLog.log("B3: ${rows.size} linhas lidas")
        if (rows.isEmpty()) throw IOException("Tabela do Fundamentus vazia")

        val kept = repository.replaceB3Table(rows)
        SyncEventLog.log("B3: $kept ativos gravados (liquidez ≥ R$ 500 mil)")
        Result.success(workDataOf("kept" to kept))
    } catch (e: Exception) {
        SyncEventLog.log("B3: falha — ${e.message ?: e.javaClass.simpleName}")
        Result.retry()
    }
}
