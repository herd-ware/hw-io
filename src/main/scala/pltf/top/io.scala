/*
 * File: io.scala                                                              *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-26 09:49:17 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.pltf

import chisel3._
import chisel3.util._

import herd.common.bus._
import herd.common.dome._
import herd.common.mem.mb4s._
import herd.io.periph.gpio._
import herd.io.periph.timer._
import herd.io.periph.uart._
import herd.io.periph.ps2._
import herd.io.periph.spi._
import herd.io.periph.spi_flash._
import herd.io.periph.i2c._
import herd.io.pltf.regmem.{RegMem, CST}
import herd.io.pltf.plic._


class IOPltf (p: IOPltfParams) extends Module {
  require((p.pPort.size == 1), "Only one port is available for IOPltf.")
  require(((p.nDataBit == 32) || (p.nDataBit == 64)), "IOPltf must have 32 or 64 data bits.")

  val io = IO(new Bundle {      
    val b_dome = if (p.useDome) Some(Vec(p.nDome, new DomeIO(p.nAddrBit, p.nDataBit))) else None

    val i_slct = if (p.useDomeSlct) Some(Input(new SlctBus(p.nDome, p.nPart, 1))) else None  
    val b_port  = Flipped(new Mb4sIO(p.pPort(0)))

    val b_gpio = new BiDirectIO(UInt(p.nGpio.W))
    val b_spi_flash = if (p.useSpiFlash) Some(new SpiIO(1)) else None
    val b_ps2_kb = if (p.usePs2Keyboard) Some(new Ps2IO()) else None
    val b_uart = Vec(p.nUart, new UartIO())
    val b_spi = MixedVec(
      for (ps <- p.pSpi) yield {
        new SpiIO(ps.nSlave)
      }
    )
    val b_i2c = Vec(p.nI2c, new I2cIO())

    val o_irq_msi = if (!p.useCeps) Some(Output(Vec(p.nHart, Bool()))) else None
    val o_irq_mei = if (!p.useCeps) Some(Output(Vec(p.nHart, Bool()))) else None
    val o_irq_lsi = if (p.useCeps) Some(Output(Vec(p.nHart, Vec(p.nCepsTrapLvl, Bool())))) else None
    val o_irq_lei = if (p.useCeps) Some(Output(Vec(p.nHart, Vec(p.nCepsTrapLvl, Bool())))) else None
  })

  // ******************************
  //            MODULE
  // ******************************
  val m_regmem = Module(new RegMem(p))
  val m_plic = Module(new Plic(p))
  val m_gpio = if (p.nGpio > 0) Some(Module(new Gpio(p.pGpio))) else None
  val m_ptimer = Seq.fill(p.nPTimer){Module(new Timer(p.pPTimer))}
  val m_spi_flash = if (p.useSpiFlash) Some(Module(new SpiFlash(p.pSpiFlash))) else None
  val m_ps2_kb = if (p.usePs2Keyboard) Some(Module(new Ps2Keyboard(p.pPs2Keyboard))) else None
  val m_uart = Seq.fill(p.nUart){Module(new Uart(p.pUart))}
  val m_spi = for (ps <- p.pSpi) yield {
    val m_spi = Module(new Spi(ps))
    m_spi
  } 
  val m_i2c = Seq.fill(p.nI2c){Module(new I2c(p.pI2c))}

  // ******************************
  //            REGMEM
  // ******************************
  if (p.useDome) m_regmem.io.b_dome.get <> io.b_dome.get

  if (p.useDomeSlct) m_regmem.io.i_slct.get := io.i_slct.get
  m_regmem.io.b_port <> io.b_port

  // ******************************
  //             PLIC
  // ******************************
  m_plic.io.b_regmem <> m_regmem.io.b_plic
  for (b <- 0 until p.nPlicCause) {
    m_plic.io.i_irq(b) := false.B
  }
  if (p.nUart > 0) m_plic.io.i_irq(ID.UART0_TX) := m_uart(0).io.o_irq_tx
  if (p.nUart > 0) m_plic.io.i_irq(ID.UART0_RX) := m_uart(0).io.o_irq_rx
  if (p.nPTimer > 0) m_plic.io.i_irq(ID.PTIMER0) := m_ptimer(0).io.o_irq
  if (p.nPTimer > 1) m_plic.io.i_irq(ID.PTIMER1) := m_ptimer(1).io.o_irq
  if (p.nPTimer > 2) m_plic.io.i_irq(ID.PTIMER2) := m_ptimer(2).io.o_irq
  if (p.nPTimer > 3) m_plic.io.i_irq(ID.PTIMER3) := m_ptimer(3).io.o_irq

  // ******************************
  //             GPIO
  // ******************************
  if (p.nGpio > 0) {
    m_gpio.get.io.b_regmem <> m_regmem.io.b_gpio.get
    m_gpio.get.io.b_gpio <> io.b_gpio
  }

  // ******************************
  //            TIMERS
  // ******************************
  for (pt <- 0 until p.nPTimer) {
    m_ptimer(pt).io.b_regmem <> m_regmem.io.b_ptimer.get(pt)
  }

  // ******************************
  //           SPI FLASH
  // ******************************
  if (p.useSpiFlash) {
    m_spi_flash.get.io.b_regmem.get <> m_regmem.io.b_spi_flash.get
    m_spi_flash.get.io.b_spi <> io.b_spi_flash.get
  }

  // ******************************
  //         PS/2 KEYBOARD
  // ******************************
  if (p.usePs2Keyboard) {
    m_ps2_kb.get.io.b_regmem.get <> m_regmem.io.b_ps2_kb.get
    m_ps2_kb.get.io.b_ps2 <> io.b_ps2_kb.get
  }  

  // ******************************
  //             UART
  // ******************************
  for (u <- 0 until p.nUart) {
    m_uart(u).io.b_regmem.get <> m_regmem.io.b_uart.get(u)
    m_uart(u).io.b_uart <> io.b_uart(u)
  }

  // ******************************
  //           SPI MASTER
  // ******************************
  for (sm <- 0 until p.nSpi) {
    m_spi(sm).io.b_regmem.get <> m_regmem.io.b_spi.get(sm)
    m_spi(sm).io.b_spi <> io.b_spi(sm)
  }

  // ******************************
  //           I2C MASTER
  // ******************************
  for (im <- 0 until p.nI2c) {
    m_i2c(im).io.b_regmem.get <> m_regmem.io.b_i2c.get(im)
    m_i2c(im).io.b_i2c <> io.b_i2c(im)
  }

  // ******************************
  //             I/Os
  // ******************************
  for (h <- 0 until p.nHart) {
    if (p.useCeps) {
      for (tl <- 0 until p.nCepsTrapLvl) {
        io.o_irq_lei.get(h)(tl) := m_plic.io.o_irq_ei(h * p.nCepsTrapLvl + tl)
        io.o_irq_lsi.get(h)(tl) := m_regmem.io.o_irq_lsi.get(tl)(h)
      }
    } else {
      io.o_irq_mei.get(h) := m_plic.io.o_irq_ei(h)
      io.o_irq_msi.get(h) := m_regmem.io.o_irq_msi.get(h) 
    }
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

object IOPltf extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new IOPltf(IOPltfConfigBase), args)
}


