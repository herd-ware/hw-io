/*
 * File: rx.scala                                                              *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:10:35 pm                                       *
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
import herd.common.tools.{Sample, Counter}


class Rx (p: UartParams) extends Module {
  import herd.io.periph.uart.UartFSM._

  val io = IO(new Bundle {
    val i_config = Input(new UartConfigBus())

    val b_out = new GenRVIO(p, Bool(), UInt(8.W))

    val i_rx = Input(Bool())
  })

  val r_fsm = RegInit(s0IDLE)

  val r_data = Reg(Vec(8, Bool()))
  val r_par_in = Reg(Bool())
  val r_par_calc = Reg(Bool())
  val r_error = Reg(Bool())

  io.b_out.valid := (r_fsm === s6END)
  io.b_out.ctrl.get := r_error
  io.b_out.data.get := r_data.asUInt

  // ******************************
  //            CONFIG
  // ******************************
  val r_config = Reg(new UartConfigBus())
  val w_half = (r_config.ncycle >> 2)

  when (r_fsm === s0IDLE) {
    r_config := io.i_config
  }

  // ******************************
  //            SAMPLE
  // ******************************
  val m_sample = Module(new Sample(Bool(), BIT.IDLE.B))
  m_sample.io.i_data := io.i_rx

  // ******************************
  //           COUNTERS
  // ******************************
  val m_ccnt = Module(new Counter(32))
  val m_bcnt = Module(new Counter(3))

  m_ccnt.io.i_limit := r_config.ncycle
  m_ccnt.io.i_init := true.B
  m_ccnt.io.i_en := false.B

  m_bcnt.io.i_limit := 0.U
  m_bcnt.io.i_init := true.B
  m_bcnt.io.i_en := false.B

  // ******************************
  //              FSM
  // ******************************
  // ------------------------------
  //            DEFAULT
  // ------------------------------
  r_fsm := s0IDLE

  switch (r_fsm) {
    // ------------------------------
    //             IDLE
    // ------------------------------
    is (s0IDLE) {
      r_par_calc := 0.B
      when (m_sample.io.o_data === BIT.START.U) {
        r_fsm := s1START
      }.otherwise {
        r_fsm := s0IDLE
      }
    }
    // ------------------------------
    //             START
    // ------------------------------
    is (s1START) {
      m_ccnt.io.i_limit := w_half
      r_par_calc := 0.B
      when (m_ccnt.io.o_flag) {
        r_fsm := Mux((m_sample.io.o_data === BIT.START.U), s2DATA, s0IDLE)
        m_ccnt.io.i_init := true.B
        m_ccnt.io.i_en := false.B
      }.otherwise {
        r_fsm := Mux((m_sample.io.o_data === BIT.START.U), s1START, s0IDLE)
        m_ccnt.io.i_init := false.B
        m_ccnt.io.i_en := true.B
      }
    }
    // ------------------------------
    //              DATA
    // ------------------------------
    is (s2DATA) {
      when (m_ccnt.io.o_flag) {
        r_data(m_bcnt.io.o_val) := m_sample.io.o_data
        r_par_calc := r_par_calc ^ m_sample.io.o_data
        m_ccnt.io.i_init := true.B
        m_ccnt.io.i_en := false.B
        when ((r_config.is8bit & (m_bcnt.io.o_val === 7.U)) | (~r_config.is8bit & (m_bcnt.io.o_val === 6.U))) {
          m_bcnt.io.i_init := true.B
          m_bcnt.io.i_en := false.B
          when (r_config.parity) {
            r_fsm := s3PARITY
          }.elsewhen (r_config.stop =/= 0.U) {
            r_fsm := s4STOP0
          }.otherwise {
            r_fsm := s6END
          }
        }.otherwise {
          r_fsm := s2DATA
          m_bcnt.io.i_init := false.B
          m_bcnt.io.i_en := true.B
        }
        
      }.otherwise {
        r_fsm := s2DATA
        m_ccnt.io.i_init := false.B
        m_ccnt.io.i_en := true.B
        m_bcnt.io.i_init := false.B
        m_bcnt.io.i_en := false.B
      }
    }
    // ------------------------------
    //             PARITY
    // ------------------------------
    is (s3PARITY) {
      when (m_ccnt.io.o_flag) {
        r_fsm := Mux(r_config.stop =/= 0.U, s4STOP0, s6END)
        r_par_in := m_sample.io.o_data
        r_error := r_par_calc ^ m_sample.io.o_data        
        m_ccnt.io.i_init := true.B
        m_ccnt.io.i_en := false.B
      }.otherwise {
        r_fsm := s3PARITY
        m_ccnt.io.i_init := false.B
        m_ccnt.io.i_en := true.B
      }
    }
    // ------------------------------
    //             STOP 0
    // ------------------------------
    is (s4STOP0) {
      when (m_ccnt.io.o_flag) {
        r_fsm := Mux(r_config.stop === 2.U, s5STOP1, s6END)
        m_ccnt.io.i_init := true.B
        m_ccnt.io.i_en := false.B
      }.otherwise {
        r_fsm := s4STOP0
        m_ccnt.io.i_init := false.B
        m_ccnt.io.i_en := true.B
      }
    }
    // ------------------------------
    //             STOP 1
    // ------------------------------
    is (s5STOP1) {
      when (m_ccnt.io.o_flag) {
        r_fsm := s6END
        m_ccnt.io.i_init := true.B
        m_ccnt.io.i_en := false.B
      }.otherwise {
        r_fsm := s5STOP1
        m_ccnt.io.i_init := false.B
        m_ccnt.io.i_en := true.B
      }
    }
    // ------------------------------
    //              END
    // ------------------------------
    is (s6END) {
      r_fsm := s0IDLE
    }
  }
}

object Rx extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Rx(UartConfigBase), args)
}
