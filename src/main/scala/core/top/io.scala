/*
 * File: io.scala                                                              *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:07:21 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core

import chisel3._
import chisel3.util._

import herd.common.dome._
import herd.common.mem.mb4s._
import herd.common.isa.priv.{IRQ => PIRQ}
import herd.common.isa.ceps.{IRQ => DIRQ}
import herd.common.isa.custom.{IRQ => CIRQ}
import herd.io.core.regmem.{RegMem}
import herd.io.core.clint.{Priv => PrivClint, Ceps => CepsClint}
import herd.io.core.clint.{ClintIO}
import herd.io.periph.timer._


class IOCore (p: IOCoreParams) extends Module {
  require((p.pPort.size == 1), "Only one port is available for IOCore.")
  require(((p.nDataBit == 32) || (p.nDataBit == 64)), "IOCore must have 32 or 64 data bits.")
  require(((p.nDataBit == 64) || (p.nCTimer <= 4)), "IOCore must have only 4 or less CTimer in 32 bits.")

  val io = IO(new Bundle {    
    val b_dome = if (p.useDome) Some(Vec(p.nDome, new DomeIO(p.nAddrBit, p.nDataBit))) else None

    val i_slct = if (p.useDomeSlct) Some(Input(new SlctBus(p.nDome, p.nPart, 1))) else None 
    val b_port = Flipped(new Mb4sIO(p.pPort(0)))

    val b_clint = new ClintIO(p.nDataBit)
    
    val i_irq_lei = if (p.useCeps) Some(Input(Vec(p.nCepsTrapLvl, Bool()))) else None
    val i_irq_lsi = if (p.useCeps) Some(Input(Vec(p.nCepsTrapLvl, Bool()))) else None
    val i_irq_mei = if (!p.useCeps) Some(Input(Bool())) else None
    val i_irq_msi = if (!p.useCeps) Some(Input(Bool())) else None
  })

  // ******************************
  //            MODULE
  // ******************************
  val m_regmem = Module(new RegMem(p))
  val m_mtimer = if (!p.useCeps) Some(Module(new Timer(p.pCTimer))) else None
  val m_ltimer = if (p.useCeps) Some(Seq.fill(p.nCepsTrapLvl){Module(new Timer(p.pCTimer))}) else None
  val m_ctimer = Seq.fill(p.nCTimer){Module(new Timer(p.pCTimer))}

  val m_clint_priv = if (!p.useCeps) Some(Module(new PrivClint(p))) else None
  val m_clint_ceps = if (p.useCeps) Some(Module(new CepsClint(p))) else None

  // ******************************
  //            REGMEM
  // ******************************
  if (p.useDome) m_regmem.io.b_dome.get <> io.b_dome.get

  if (p.useDomeSlct) m_regmem.io.i_slct.get := io.i_slct.get
  m_regmem.io.b_port <> io.b_port

  // ******************************
  //             CLINT
  // ******************************
  // ------------------------------
  //             CEPS
  // ------------------------------
  if (p.useCeps) {
    // Default
    m_clint_ceps.get.io.b_core <> io.b_clint
    for (b <- 0 until p.nDataBit) {
      m_clint_ceps.get.io.i_irq(b) := false.B
    }

    // Trap level 0
    if (p.nCepsTrapLvl > 0) {
      m_clint_ceps.get.io.i_irq(DIRQ.L0EI) := io.i_irq_lei.get(0)
      m_clint_ceps.get.io.i_irq(DIRQ.L0SI) := io.i_irq_lsi.get(0)
      m_clint_ceps.get.io.i_irq(DIRQ.L0TI) := m_ltimer.get(0).io.o_irq
    }

    // Trap level 1
    if (p.nCepsTrapLvl > 1) {
      m_clint_ceps.get.io.i_irq(DIRQ.L1EI) := io.i_irq_lei.get(1)
      m_clint_ceps.get.io.i_irq(DIRQ.L1SI) := io.i_irq_lsi.get(1)
      m_clint_ceps.get.io.i_irq(DIRQ.L1TI) := m_ltimer.get(1).io.o_irq
    }

    // Custom timers
    if (p.nCTimer >= 1) m_clint_ceps.get.io.i_irq(CIRQ.CTIMER0) := m_ctimer(0).io.o_irq
    if (p.nCTimer >= 2) m_clint_ceps.get.io.i_irq(CIRQ.CTIMER1) := m_ctimer(1).io.o_irq
    if (p.nCTimer >= 3) m_clint_ceps.get.io.i_irq(CIRQ.CTIMER2) := m_ctimer(2).io.o_irq
    if (p.nCTimer >= 4) m_clint_ceps.get.io.i_irq(CIRQ.CTIMER3) := m_ctimer(3).io.o_irq

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
  //             CEPS
  // ------------------------------
  if (p.useCeps) {
    for (tl <- 0 until p.nCepsTrapLvl) {
      m_ltimer.get(tl).io.b_regmem <> m_regmem.io.b_ltimer.get(tl)
    }
  // ------------------------------
  //             PRIV
  // ------------------------------
  } else {
    m_mtimer.get.io.b_regmem <> m_regmem.io.b_mtimer.get
  }
  
  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    for (d <- 0 until p.nDome) {
      io.b_dome.get(d).free := m_regmem.io.b_dome.get(d).free
    }
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    
  } 
}

object IOCore extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new IOCore(IOCoreConfigBase), args)
}


