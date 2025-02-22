package bzh.klabz.squadrating.datastores

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import bzh.klabz.squadrating.SquadratingExtension
import bzh.klabz.squadrating.data.ExploredSquadrats
import java.io.InputStream
import java.io.OutputStream

object ExploredSquadratsSerializer : Serializer<ExploredSquadrats> {

    override val defaultValue: ExploredSquadrats = ExploredSquadrats.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ExploredSquadrats {
        try {
            return ExploredSquadrats.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            Log.e(SquadratingExtension.TAG, "Failed to read protobuf")
            return ExploredSquadrats.newBuilder().build()
        }
    }

    override suspend fun writeTo(t: ExploredSquadrats, output: OutputStream) =
        t.writeTo(output)
}

val Context.exploredSquadratsDataStore: DataStore<ExploredSquadrats> by dataStore(fileName = "explored_tiles.pb",
    serializer = ExploredSquadratsSerializer
)
