/*
 * File: io.scala
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-21 04:51:45 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core

import chisel3._
import chisel3.util._

import herd.common.field._
import herd.common.core.{HpcPipelineBus,HpcMemoryBus}
import herd.common.mem.mb4s._
import herd.common.isa.priv.{IRQ => PIRQ}
import herd.common.isa.champ.{IRQ => HFIRQ}
import herd.common.isa.custom.{IRQ => CIRQ}
import herd.io.core.regmem.{RegMem}
import herd.io.core.clint.{Priv => PrivClint, Champ => ChampClint}
import herd.io.core.clint.{ClintIO}
import herd.io.core.hpm.{Hpm}
import herd.io.periph.timer._


class IOCore (p: IOCoreParams) extends Module {
  require((p.pPort.size == 1), "Only one port is available for IOCore.")
  require(((p.nDataBit == 32) || (p.nDataBit == 64)), "IOCore must have 32 or 64 data bits.")
  require(((p.nDataBit == 64) || (p.nCTimer <= 4)), "IOCore must have only 4 or less CTimer in 32 bits.")

  val io = IO(new Bundle {    
    val b_field = if (p.useField) Some(Vec(p.nField, new FieldIO(p.nAddrBit, p.nDataBit))) else None

    val i_slct = if (p.useFieldSlct) Some(Input(new SlctBus(p.nField, p.nPart, 1))) else None 
    val b_port = Flipped(new Mb4sIO(p.pPort(0)))

    val b_clint = new ClintIO(p.nDataBit)
    val i_hpc_pipe = Input(Vec(p.nHart, new HpcPipelineBus()))
    val i_hpc_mem = Input(Vec(p.nHart, new HpcMemoryBus()))
    val o_hpm = Output(Vec(p.nHart, Vec(32, UInt(64.W))))
    
    val i_irq_lei = if (p.useChamp) Some(Input(Vec(p.nChampTrapLvl, Bool()))) else None
    val i_irq_lsi = if (p.useChamp) Some(Input(Vec(p.nChampTrapLvl, Bool()))) else None
    val i_irq_mei = if (!p.useChamp) Some(Input(Bool())) else None
    val i_irq_msi = if (!p.useChamp) Some(Input(Bool())) else None

    val o_dbg = if (p.debug) Some(Output(new IOCoreDbgBus(p))) else None
  })

  // ******************************
  //            MODULE
  // ******************************
  val m_regmem = Module(new RegMem(p))
  val m_mtimer = if (!p.useChamp) Some(Module(new Timer(p.pCTimer))) else None
  val m_ltimer = if (p.useChamp) Some(Seq.fill(p.nChampTrapLvl){Module(new Timer(p.pCTimer))}) else None
  val m_ctimer = Seq.fill(p.nCTimer){Module(new Timer(p.pCTimer))}

  val m_clint_priv = if (!p.useChamp) Some(Module(new PrivClint(p))) else None
  val m_clint_champ = if (p.useChamp) Some(Module(new ChampClint(p))) else None
  val m_hpm = Module(new Hpm(p))

  // ******************************
  //            REGMEM
  // ******************************
  if (p.useField) m_regmem.io.b_field.get <> io.b_field.get

  if (p.useFieldSlct) m_regmem.io.i_slct.get := io.i_slct.get
  m_regmem.io.b_port <> io.b_port

  // ******************************
  //             CLINT
  // ******************************
  // ------------------------------
  //             CHAMP
  // ------------------------------
  if (p.useChamp) {
    // Default
    m_clint_champ.get.io.b_core <> io.b_clint
    for (b <- 0 until p.nDataBit) {
      m_clint_champ.get.io.i_irq(b) := false.B
    }

    // Trap level 0
    if (p.nChampTrapLvl > 0) {
      m_clint_champ.get.io.i_irq(HFIRQ.L0EI) := io.i_irq_lei.get(0)
      m_clint_champ.get.io.i_irq(HFIRQ.L0SI) := io.i_irq_lsi.get(0)
      m_clint_champ.get.io.i_irq(HFIRQ.L0TI) := m_ltimer.get(0).io.o_irq
    }

    // Trap level 1
    if (p.nChampTrapLvl > 1) {
      m_clint_champ.get.io.i_irq(HFIRQ.L1EI) := io.i_irq_lei.get(1)
      m_clint_champ.get.io.i_irq(HFIRQ.L1SI) := io.i_irq_lsi.get(1)
      m_clint_champ.get.io.i_irq(HFIRQ.L1TI) := m_ltimer.get(1).io.o_irq
    }

    // Custom timers
    if (p.nCTimer >= 1) m_clint_champ.get.io.i_irq(CIRQ.CTIMER0) := m_ctimer(0).io.o_irq
    if (p.nCTimer >= 2) m_clint_champ.get.io.i_irq(CIRQ.CTIMER1) := m_ctimer(1).io.o_irq
    if (p.nCTimer >= 3) m_clint_champ.get.io.i_irq(CIRQ.CTIMER2) := m_ctimer(2).io.o_irq
    if (p.nCTimer >= 4) m_clint_champ.get.io.i_irq(CIRQ.CTIMER3) := m_ctimer(3).io.o_irq

  // ------------------------------
  //             PRIV
  // ------------------------------
  } else {
    // Default
    m_clint_priv.get.io.b_core <> io.b_clint
    for (b <- 0 until p.nDataBit) {
      m_clint_priv.get.io.i_irq(b) := false.B
    }

    // Machine level
    m_clint_priv.get.io.i_irq(PIRQ.MEI) := io.i_irq_mei.get
    m_clint_priv.get.io.i_irq(PIRQ.MSI) := io.i_irq_msi.get
    m_clint_priv.get.io.i_irq(PIRQ.MTI) := m_mtimer.get.io.o_irq

    // Custom timers
    if (p.nCTimer >= 1) m_clint_priv.get.io.i_irq(CIRQ.CTIMER0) := m_ctimer(0).io.o_irq
    if (p.nCTimer >= 2) m_clint_priv.get.io.i_irq(CIRQ.CTIMER1) := m_ctimer(1).io.o_irq
    if (p.nCTimer >= 3) m_clint_priv.get.io.i_irq(CIRQ.CTIMER2) := m_ctimer(2).io.o_irq
    if (p.nCTimer >= 4) m_clint_priv.get.io.i_irq(CIRQ.CTIMER3) := m_ctimer(3).io.o_irq
  }

  // ******************************
  //            TIMERS
  // ******************************
  for (c <- 0 until p.nCTimer) {
    m_ctimer(c).io.b_regmem <> m_regmem.io.b_ctimer.get(c)
  }
  // ------------------------------
  //            CHAMP
  // ------------------------------
  if (p.useChamp) {
    for (tl <- 0 until p.nChampTrapLvl) {
      m_ltimer.get(tl).io.b_regmem <> m_regmem.io.b_ltimer.get(tl)
    }
  // ------------------------------
  //             PRIV
  // ------------------------------
  } else {
    m_mtimer.get.io.b_regmem <> m_regmem.io.b_mtimer.get
  }

  // ******************************
  //              HPM
  // ******************************
  m_hpm.io.i_pipe := io.i_hpc_pipe
  m_hpm.io.i_mem := io.i_hpc_mem
  io.o_hpm := m_hpm.io.o_csr
  
  // ******************************
  //            FIELD
  // ******************************
  if (p.useField) {
    for (f <- 0 until p.nField) {
      io.b_field.get(f).free := m_regmem.io.b_field.get(f).free
    }
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    io.o_dbg.get.hpc := m_hpm.io.o_hpc
  } 
}

object IOCore extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new IOCore(IOCoreConfigBase), args)
}


