/*
 * File: priv.scala
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-04 07:50:03 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core.clint

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

import herd.common.isa.priv._


class Priv (p: ClintParams) extends Module {
  require(!p.useChamp, "Priv Clint needs the use of RISC-V Priv ISA.")
  require(((p.nDataBit == 32) || (p.nDataBit == 64)), "Clint must have 32 or 64 bits.")
  
  val io = IO( new Bundle {
    val b_core = new ClintIO(p.nDataBit)
    val i_irq = Input(Vec(p.nDataBit , Bool()))
  })

  val r_en = RegInit(0.B)
  val r_ip = RegInit(VecInit(Seq.fill(p.nDataBit)(0.B)))

  // ******************************
  //        INTERRUPT PRIORITY
  // ******************************
  val w_prio = Wire(Vec(p.nDataBit, UInt(log2Ceil(p.nClintPrio).W)))

  for (b <- 0 until p.nDataBit) {
    w_prio(b) := 1.U
  }

  // Fixed priority: MEI > MSI > MTI > SEI > SSI > STI
  w_prio(IRQ.MEI) := (p.nClintPrio - 1).U
  w_prio(IRQ.MSI) := (p.nClintPrio - 2).U
  w_prio(IRQ.MTI) := (p.nClintPrio - 3).U
  w_prio(IRQ.SEI) := (p.nClintPrio - 4).U
  w_prio(IRQ.SSI) := (p.nClintPrio - 5).U
  w_prio(IRQ.STI) := (p.nClintPrio - 6).U

  // ******************************
  //        SELECT INTERRUPT
  // ******************************
  // Enabled interrupts
  // If interrupt enable in r_ip, set the corresponding bit in the priority vector
  val w_en = Wire(Vec(p.nClintPrio, Vec(p.nDataBit , Bool())))         

  for (pr <- 0 until p.nClintPrio) {
    for (b <- 0 until p.nDataBit) {
      w_en(pr)(b) := r_ip(b) & (pr.U === w_prio(b))
    }
  }

  // Sorts interrupts by priority
  val w_pen = Wire(Vec(p.nClintPrio, Bool()))
  val w_pcause = Wire(Vec(p.nClintPrio, UInt(log2Ceil(p.nDataBit).W)))

  for (pr <- 0 until p.nClintPrio) {
    w_pen(pr) := w_en(pr).asUInt.orR
    w_pcause(pr) := PriorityEncoder(w_en(pr).asUInt)
  }
  w_pen(0) := false.B

  // Selects most important interrupt
  val w_pprio = Wire(UInt(log2Ceil(p.nClintPrio).W))
  // Reverse to have high priority in LSB
  w_pprio := PriorityEncoder(Reverse(w_pen.asUInt))

  // ******************************
  //        PENDING INTERRUPTS
  // ******************************
  r_en := false.B
  for (b <- 0 until p.nDataBit) {
    r_ip(b) := (r_ip(b) & ~io.b_core.ir(b)) | (io.b_core.ie(b) & io.i_irq(b))

    when (~r_ip(b) & (io.b_core.ie(b) & io.i_irq(b))) {
      r_en := true.B
    }
  }

  // ******************************
  //            OUTPUTS
  // ******************************
  io.b_core.en := r_en
  io.b_core.ecause := w_pcause((p.nClintPrio - 1).U - w_pprio) // Consider previous reverse
  io.b_core.ip := r_ip.asUInt

  // ******************************
  //            DEBUG
  // ******************************
  dontTouch(w_en)
}

object Priv extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Priv(ClintConfigBase), args)
}

