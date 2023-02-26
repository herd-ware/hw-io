/*
 * File: gpio.scala                                                            *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:08:24 pm                                       *
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

import herd.common.bus._


class Gpio (p: GpioParams) extends Module {
  val io = IO(new Bundle {
    val b_regmem = new GpioRegMemIO(p.nGpio32b)    

    val b_gpio = new BiDirectIO(UInt((p.nGpio32b * 32).W))
  })

  val r_eno = RegInit(VecInit(Seq.fill(p.nGpio32b)(0.U(32.W))))
  val r_reg = RegInit(VecInit(Seq.fill(p.nGpio32b)(0.U(32.W))))

  val w_reg = Wire(Vec(p.nGpio32b, UInt(32.W)))
  val w_eno = Wire(Vec(p.nGpio32b, UInt(32.W)))
  val w_out = Wire(Vec(p.nGpio32b, UInt(32.W)))

  // ******************************
  //            CONFIG
  // ******************************
  // ------------------------------
  //             READ
  // ------------------------------ 
  io.b_regmem.eno := r_eno

  // ------------------------------
  //             WRITE
  // ------------------------------ 
  for (g32 <- 0 until p.nGpio32b) {
    when (io.b_regmem.wen(g32 * 2)) {
      r_eno(g32) := io.b_regmem.wdata
    }
  }

  // ******************************
  //           REGISTER
  // ******************************
  for (g32 <- 0 until p.nGpio32b) {
    w_reg(g32) := r_reg(g32)
    w_eno(g32) := r_eno(g32)
    w_out(g32) := r_reg(g32) & r_eno(g32)
  }

  io.b_gpio.eno := w_eno.asUInt
  io.b_gpio.out := w_out.asUInt

  // ------------------------------
  //             READ
  // ------------------------------ 
  io.b_regmem.reg := r_reg

  // ------------------------------
  //             WRITE
  // ------------------------------ 
  for (g32 <- 0 until p.nGpio32b) {
    when (io.b_regmem.wen(g32 * 2 + 1)) {
      w_reg(g32) := io.b_regmem.wdata
    }
  }

  for (g32 <- 0 until p.nGpio32b) {
    r_reg(g32) := (r_eno(g32) & w_reg(g32)) | (~r_eno(g32) & io.b_gpio.in((g32 + 1) * 32 - 1, g32 * 32))
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    
  }
}

object Gpio extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Gpio(GpioConfigBase), args)
}