/*
 * File: uart.scala                                                            *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-03 10:11:40 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.uart

import chisel3._
import chisel3.util._
import scala.math._

import herd.common.gen._


class Uart(p: UartParams) extends Module {
  require ((p.nDataByte <= 8), "Uart allows to simultaneously read or write only 8 or less bytes.")
  
  val io = IO(new Bundle {
    val o_irq_tx = Output(Bool())
    val o_irq_rx = Output(Bool())

    val b_regmem = if (p.useRegMem) Some(new UartRegMemIO(p, p.nDataByte)) else None

    val o_status = if (!p.useRegMem) Some(Output(new UartStatusBus())) else None
    val i_config = if (!p.useRegMem) Some(Input(new UartConfigBus())) else None
    val b_port = if (!p.useRegMem) Some(new UartPortIO(p, p.nDataByte)) else None

    val b_uart = new UartIO()
  })

  val m_send = Module (new GenFifo(p, UInt(0.W), UInt(8.W), 4, p.nBufferDepth, p.nDataByte, 1))
  val m_tx = Module(new Tx(p))
  val m_rx = Module(new Rx(p))
  val m_rec = Module (new GenFifo(p, Bool(), UInt(8.W), 4, p.nBufferDepth, 1, p.nDataByte))

  val r_status = Reg(new UartStatusBus())
  val r_config = Reg(new UartConfigBus())

  val w_status = Wire(new UartStatusBus())
  val w_config = Wire(new UartConfigBus())
  val w_idle = Wire(Bool())  

  // ******************************
  //            STATUS
  // ******************************
  w_status := r_status

  w_status.full(0) := ~m_send.io.b_in(0).ready
  w_status.full(1) := ~m_send.io.b_in(1).ready
  w_status.full(2) := ~m_send.io.b_in(3).ready
  if (p.nDataByte > 4) {
    w_status.full(3) := ~m_send.io.b_in(7).ready 
  } else {
    w_status.full(3) := true.B
  }

  w_status.av(0) := m_rec.io.o_val(0).valid
  w_status.av(1) := m_rec.io.o_val(1).valid
  w_status.av(2) := m_rec.io.o_val(3).valid
  if (p.nDataByte >= 4) {
    w_status.av(3) := m_rec.io.o_val(7).valid
  } else {
    w_status.av(3) := false.B
  }

  w_idle := ~m_send.io.b_out(0).valid & m_tx.io.o_idle

  // ------------------------------
  //             READ
  // ------------------------------ 
  if (p.useRegMem) {
    io.b_regmem.get.status := Cat(  0.U(8.W),
                                    0.U(4.W), w_status.av.asUInt,
                                    0.U(4.W), w_status.full.asUInt,
                                    0.U(7.W), w_status.idle)
  } else {
    io.o_status.get := r_status
  }

  // ------------------------------
  //             WRITE
  // ------------------------------ 
  r_status.idle := w_idle

  // ******************************
  //            CONFIG
  // ******************************
  w_config := r_config

  // ------------------------------
  //             READ
  // ------------------------------ 
  if (p.useRegMem) {
    io.b_regmem.get.config := Cat(  0.U((8 - IRQ.NBIT).W), r_config.irq,
                                    0.U(8.W),
                                    0.U(8.W),
                                    0.U(3.W), r_config.stop, r_config.parity, r_config.is8bit, r_config.en)
    io.b_regmem.get.cycle := r_config.cycle
  }

  // ------------------------------
  //             WRITE
  // ------------------------------ 
  if (p.useRegMem) {
    when (io.b_regmem.get.wen(1)) {
      r_config.en := io.b_regmem.get.wdata(0)
      r_config.is8bit := io.b_regmem.get.wdata(1)
      r_config.parity := io.b_regmem.get.wdata(2)
      r_config.stop := io.b_regmem.get.wdata(4, 3)
      r_config.irq := io.b_regmem.get.wdata(24 + IRQ.NBIT - 1, 24)
    }
  
    when (io.b_regmem.get.wen(2)) {
      r_config.cycle := io.b_regmem.get.wdata
    } 
  } else {
    r_config := io.i_config.get
  }
  // ******************************
  //              TX
  // ******************************
  // ------------------------------
  //             FIFO
  // ------------------------------
  m_send.io.i_flush := false.B

  if (p.useRegMem) {
    io.b_regmem.get.send <> m_send.io.b_in
  } else {
    io.b_port.get.send <> m_send.io.b_in
  } 

  // ------------------------------
  //           CONNECT
  // ------------------------------
  m_tx.io.i_config := w_config
  
  m_send.io.b_out(0) <> m_tx.io.b_in

  io.b_uart.tx := m_tx.io.o_tx

  // ******************************
  //              RX
  // ******************************  
  // ------------------------------
  //           CONNECT
  // ------------------------------
  m_rx.io.i_config := w_config

  m_rx.io.i_rx := io.b_uart.rx

  m_rec.io.b_in(0) <> m_rx.io.b_out

  // ------------------------------
  //             FIFO
  // ------------------------------
  m_rec.io.i_flush := false.B

  if (p.useRegMem) {
    m_rec.io.b_out <> io.b_regmem.get.rec
  } else {
    m_rec.io.b_out <> io.b_port.get.rec
  }

  // ******************************
  //           INTERRUPT
  // ******************************
  io.o_irq_tx := ~r_status.idle & w_idle

  io.o_irq_rx := false.B
  switch (w_config.irq) {
    is (IRQ.B1)   { io.o_irq_rx := m_rec.io.o_val(0).valid}
    is (IRQ.B2)   { io.o_irq_rx := m_rec.io.o_val(1).valid}
    is (IRQ.B4)   { io.o_irq_rx := m_rec.io.o_val(3).valid}
    is (IRQ.B8)   {
      if (p.nDataByte >= 8) {
                    io.o_irq_rx := m_rec.io.o_val(7).valid
      }      
    }
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    
  }
}

object Uart extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Uart(UartConfigBase), args)
}
