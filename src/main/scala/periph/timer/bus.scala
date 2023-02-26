/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:09:57 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.timer

import chisel3._
import chisel3.util._



// ******************************
//            CONTROL
// ******************************
class TimerStatusBus extends Bundle {
  val over = Bool()
}

class TimerConfigBus extends Bundle {
  val en = Bool()
}

// ******************************
//       MEMORY REGISTER BUS
// ******************************
class TimerRegMemIO extends Bundle {
  val wen = Input(Vec(6, Bool()))  
  val wdata = Input(UInt(64.W))    
  
  val status = Output(UInt(32.W))
  val config = Output(UInt(32.W))
  val cnt = Output(UInt(64.W))
  val cmp = Output(UInt(64.W)) 
}




