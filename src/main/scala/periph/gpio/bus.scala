/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:08:18 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.gpio

import chisel3._
import chisel3.util._


// ******************************
//       MEMORY REGISTER BUS
// ******************************
class GpioRegMemIO (nGpio32b: Int) extends Bundle {
  val wen = Input(Vec(2 * nGpio32b, Bool()))  
  val wdata = Input(UInt(32.W))    

  val eno = Output(Vec(nGpio32b, UInt(32.W)))  
  val reg = Output(Vec(nGpio32b, UInt(32.W)))
}
