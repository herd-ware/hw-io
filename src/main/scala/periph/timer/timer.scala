/*
 * File: timer.scala
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-04 07:29:57 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.timer

import chisel3._
import chisel3.util._


class Timer(p: TimerParams) extends Module {
  val io = IO(new Bundle {
    val o_irq = Output(Bool())

    val b_regmem = new TimerRegMemIO()    
  })

  val r_status = Reg(new TimerStatusBus())
  val r_config = Reg(new TimerConfigBus())
  val r_cnt = RegInit(0.U(64.W))
  val r_cmp = RegInit(0.U(64.W))

  val w_status = Wire(new TimerStatusBus())
  val w_config = Wire(new TimerConfigBus())
  val w_over = Wire(Bool())

  // ******************************
  //            STATUS
  // ******************************
  w_status := r_status

  // ------------------------------
  //             READ
  // ------------------------------ 
  io.b_regmem.status := Cat(  0.U(8.W),
                              0.U(8.W), 
                              0.U(8.W),
                              0.U(7.W), w_over)

  // ------------------------------
  //             WRITE
  // ------------------------------ 

  // ******************************
  //            CONFIG
  // ******************************
  w_config := r_config

  // ------------------------------
  //             READ
  // ------------------------------ 
  io.b_regmem.config := Cat(  0.U(8.W),
                              0.U(8.W), 
                              0.U(8.W),
                              0.U(7.W), r_config.en)

  // ------------------------------
  //             WRITE
  // ------------------------------ 
  when (io.b_regmem.wen(1)) {
    r_config.en := io.b_regmem.wdata(0)
  }

  // ******************************
  //            COUNTER
  // ******************************
  // ------------------------------
  //             READ
  // ------------------------------  
  io.b_regmem.cnt := r_cnt
  io.b_regmem.cmp := r_cmp

  // ------------------------------
  //             WRITE
  // ------------------------------   
  when (io.b_regmem.wen(2)) {
    r_cnt := Cat(r_cnt(63, 32), io.b_regmem.wdata)
  }
  when (io.b_regmem.wen(3)) {
    r_cnt := Cat(io.b_regmem.wdata, r_cnt(31, 0))
  }
  when (io.b_regmem.wen(2) & io.b_regmem.wen(3)) {
    r_cnt := io.b_regmem.wdata
  }

  when (io.b_regmem.wen(4)) {
    r_cmp := Cat(r_cmp(63, 32), io.b_regmem.wdata)
  }
  when (io.b_regmem.wen(5)) {
    r_cmp := Cat(io.b_regmem.wdata, r_cmp(31, 0))
  }
  when (io.b_regmem.wen(4) & io.b_regmem.wen(5)) {
    r_cmp := io.b_regmem.wdata
  }

  // ******************************
  //            TIMER
  // ******************************
  when (~(io.b_regmem.wen(4) | io.b_regmem.wen(5))) {
    when (r_config.en) {
      r_cnt := r_cnt + 1.U
    }.otherwise {
      r_cnt := 0.U
    }  
  }

  w_over := r_config.en & (r_cnt >= r_cmp)

  // ******************************
  //           INTERRUPT
  // ******************************
  io.o_irq := w_over

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    
  }
}

object Timer extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Timer(TimerConfigBase), args)
}