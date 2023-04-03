/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-03 10:08:59 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.i2c

import chisel3._
import chisel3.util._
import scala.math._

import herd.common.bus._
import herd.common.gen._


// ******************************
//              I2C
// ******************************
class I2cIO extends Bundle {
  val scl = new BiDirectIO(Bool())
  val sda = new BiDirectIO(Bool())
}

// ******************************
//            CONTROL
// ******************************
class I2cStatusBus extends Bundle {
  val idle = Bool()
  val err_aa = Bool()
  val err_da = Bool()
  val full = Vec(4, Bool())
  val av = Vec(4, Bool())
}

class I2cConfigBus extends Bundle {
  val en = Bool()
  val irq = UInt(IRQ.NBIT.W)
  val cycle = UInt(32.W)
  val addr = UInt(8.W)
}

class I2cCtrlBus extends Bundle {
  val rw = Bool()
  val mb = Bool()
}

// ******************************
//        MEMORY REGISTER
// ******************************
class I2cRegMemIO(p: GenParams, nDataByte: Int) extends Bundle {
  val wen = Input(Vec(4, Bool()))  
  val wdata = Input(UInt(32.W))    

  val status = Output(UInt(32.W))
  val config = Output(UInt(32.W))
  val cycle = Output(UInt(32.W))
  val addr = Output(UInt(8.W))
  
  val creq = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(8.W), UInt(0.W))))
  val dreq = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(0.W), UInt(8.W))))
  val read = Vec(nDataByte, new GenRVIO(p, UInt(0.W), UInt(8.W)))
}

// ******************************
//              PORT
// ******************************
class I2cPortIO(p: GenParams, nDataByte: Int) extends Bundle {  
  val creq = Vec(nDataByte, Flipped(new GenRVIO(p, new I2cCtrlBus(), UInt(0.W))))
  val dreq = Vec(nDataByte, Flipped(new GenRVIO(p, UInt(0.W), UInt(8.W))))
  val read = Vec(nDataByte, new GenRVIO(p, UInt(0.W), UInt(8.W)))
}

