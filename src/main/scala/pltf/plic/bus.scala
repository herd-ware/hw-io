/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:10:50 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.pltf.plic

import chisel3._
import chisel3.util._



// ******************************
//        CONFIGURATION BUS
// ******************************
class PlicConfigBus extends Bundle {
  val threshold = UInt(32.W)
  val claim = UInt(32.W)
}

// ******************************
//       MEMORY REGISTER BUS
// ******************************
class PlicRegMemIO (p: PlicParams) extends Bundle {
  val wsip = Input(Vec(p.nPlicCause, Bool()))  
  val wip = Input(Vec(p.nPlicCause32b, Bool())) 
  val wattr = Input(Vec(p.nPlicCause, Bool()))  
  val wen = Input(Vec(p.nPlicContext, Vec(p.nPlicCause32b, Bool())))  
  val wcfg = Input(Vec(p.nPlicContext, Vec(2, Bool())))  
  val wdata = Input(UInt(p.nDataBit.W))    

  val sip = Output(Vec(p.nPlicCause, UInt(32.W)))
  val ip = Output(Vec(p.nPlicCause32b, UInt(32.W)))
  val attr = Output(Vec(p.nPlicCause, UInt(8.W)))
  val en = Output(Vec(p.nPlicContext, Vec(p.nPlicCause32b, UInt(32.W))))
  val cfg = Output(Vec(p.nPlicContext, new PlicConfigBus()))
}