package org.pytorch.serve.snapshot

import org.pytorch.serve.servingsdk.impl.PluginsManager
import org.pytorch.serve.servingsdk.snapshot.SnapshotSerializer

object SnapshotSerializerFactory {
  private var snapshotSerializer: SnapshotSerializer = null

  private def initialize(): Unit = {
    snapshotSerializer = PluginsManager.getInstance.getSnapShotSerializer
    if (snapshotSerializer == null) snapshotSerializer = new FSSnapshotSerializer
  }

  def getSerializer: SnapshotSerializer = {
    if (snapshotSerializer == null) SnapshotSerializerFactory.initialize()
    snapshotSerializer
  }
}

final class SnapshotSerializerFactory  {
}