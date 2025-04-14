package org.pytorch.serve.device

//object AcceleratorVendor extends Enumeration {
//  type AcceleratorVendor = Value
//  val AMD, NVIDIA, INTEL, APPLE, UNKNOWN = Value
//}

enum AcceleratorVendor:
  case  AMD, NVIDIA, INTEL, APPLE, UNKNOWN 
