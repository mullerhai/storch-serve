package org.pytorch.serve.util

//object ConnectorType extends Enumeration {
//  type ConnectorType = Value
//  val INFERENCE_CONNECTOR, MANAGEMENT_CONNECTOR, METRICS_CONNECTOR, ALL, OPEN_INFERENCE_CONNECTOR = Value
//}

enum ConnectorType:
  case INFERENCE_CONNECTOR, MANAGEMENT_CONNECTOR, METRICS_CONNECTOR, ALL, OPEN_INFERENCE_CONNECTOR