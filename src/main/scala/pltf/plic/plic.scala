/*
 * File: plic.scala                                                            *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:11:06 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.pltf.plic

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

class PlicPriority (p: PlicParams) extends Module {
  val io = IO( new Bundle {
    val i_prio = Input(UInt(log2Ceil(p.nPlicPrio).W))
    val i_ip = Input(UInt((p.nPlicCause32b * 32).W))
    val i_en = Input(UInt((p.nPlicCause32b * 32).W))
    val i_sip = Input(Vec(p.nPlicCause, UInt(log2Ceil(p.nPlicPrio).W)))

    val o_pav = Output(Bool())
    val o_pid = Output(UInt(log2Ceil(p.nPlicCause).W))
  })

  // ------------------------------
  //           AVAILABLE
  // ------------------------------
  val w_av = Wire(Vec(p.nPlicCause , Bool()))

  for (ca <- 0 until p.nPlicCause) {
    w_av(ca) := io.i_en(ca) & io.i_ip(ca) & (io.i_sip(ca) === io.i_prio)
  }  
  
  // ------------------------------
  //         PRIORITY SORT
  // ------------------------------  
  io.o_pav := w_av.asUInt.orR
  io.o_pid := PriorityEncoder(w_av.asUInt)
}

class Plic (p: PlicParams) extends Module {
  require(((p.nDataBit == 32) || (p.nDataBit == 64)), "PLIC must have 32 or 64 bits.")  
  
  val io = IO( new Bundle {
    val b_regmem = new PlicRegMemIO(p)

    val o_irq_ei = Output(Vec(p.nPlicContext, Bool()))
    val i_irq = Input(Vec(p.nPlicCause, Bool()))
  })

  val r_irq = Reg(Vec(p.nPlicCause32b * 32, Bool()))

  val r_rst = RegInit(true.B)
  val r_sip = Reg(Vec(p.nPlicCause, UInt(32.W)))
  val r_ip = Reg(Vec(p.nPlicCause32b, UInt(32.W)))
  val r_attr = Reg(Vec(p.nPlicCause, UInt(8.W)))
  val r_en = Reg(Vec(p.nPlicContext, Vec(p.nPlicCause32b, UInt(32.W))))
  val r_cfg = Reg(Vec(p.nPlicContext, new PlicConfigBus()))

  // ******************************
  //             TABLES
  // ******************************
//  val w_wip_clear = Wire(Vec(p.nPlicCause32b, UInt(32.W)))

  val w_rclaim_id = Wire(Vec(p.nPlicContext, UInt(log2Ceil(p.nPlicCause).W)))
  val w_wclaim_clear = Wire(Vec(p.nPlicCause32b * 32, Bool()))
//  val w_wclaim_en = Wire(Vec(p.nPlicContext, Bool()))
//  val w_wclaim_id = Wire(Vec(p.nPlicContext, UInt(log2Ceil(p.nPlicCause).W)))
  // ------------------------------
  //             RESET
  // ------------------------------
  r_rst := false.B

  when (r_rst) {
    for (ca <- 0 until p.nPlicCause) {
      r_sip(ca) := 0.U
    }
    for (ca <- 0 until p.nPlicCause32b) {
      r_ip(ca) := 0.U
    }
    for (co <- 0 until p.nPlicContext) {
      for (ca <- 0 until p.nPlicCause32b) {
        r_en(co)(ca) := 0.U
      }
      r_cfg(co).threshold := 0.U
      r_cfg(co).claim := 0.U
    }
  }

  // ------------------------------
  //             WRITE
  // ------------------------------
  // Priority
  r_sip(0) := 0.U
  for (ca <- 1 until p.nPlicCause) {
    when (io.b_regmem.wsip(ca)) {
      r_sip(ca) := Cat(0.U(32 - log2Ceil(p.nPlicPrio)), io.b_regmem.wdata(log2Ceil(p.nPlicPrio) - 1, 0))
    }
  }

  // Pending
//  for (ca <- 0 until p.nPlicCause32b) {
//    w_wip_clear(ca) := Cat(Fill(32, 1.B))
//    when (io.b_regmem.wip(ca)) {
//      w_wip_clear(ca) := ~io.b_regmem.wdata
//    }
//    if ((p.nDataBit >= 64) && (ca > 0)) {
//      when (io.b_regmem.wip(ca - 1) & io.b_regmem.wip(ca)) {
//        w_wip_clear(ca) := ~(io.b_regmem.wdata(63, 32))
//      }
//    }
//  }

  // Attribute
  r_attr(0) := 0.U
  for (ca <- 1 until p.nPlicCause) {
    when (io.b_regmem.wattr(ca)) {
      r_attr(ca) := Cat(0.U(5.W), io.b_regmem.wdata(2, 1), 0.U(1.W))
    }
  }

  // Enable
  for (co <- 0 until p.nPlicContext) {
    for (ca <- 0 until p.nPlicCause32b) {
      when (io.b_regmem.wen(co)(ca)) {
        if (ca == 0) {
          r_en(co)(ca) := Cat(io.b_regmem.wdata(31, 1), 0.U(1.W))
        } else {
          r_en(co)(ca) := io.b_regmem.wdata
        }        
      } 
    }
  }

//  // Configuration
//  for (co <- 0 until p.nPlicContext) {
//    w_wclaim_en(co) := false.B
//    w_wclaim_id(co) := 0.U
//
//    when (io.b_regmem.wcfg(co)(0)) {
//      r_cfg(co).threshold := Cat(0.U(32 - log2Ceil(p.nPlicPrio)), io.b_regmem.wdata(log2Ceil(p.nPlicPrio) - 1, 0))
//    }     
//    r_cfg(co).claim := w_rclaim_id(co)
//    when (io.b_regmem.wcfg(co)(1)) {
//      w_wclaim_en(co) := true.B
//      w_wclaim_id(co) := io.b_regmem.wdata(log2Ceil(p.nPlicCause) - 1, 0)
//    }  
//  }

  // Config: Threshold
  for (co <- 0 until p.nPlicContext) {
    when (io.b_regmem.wcfg(co)(0)) {
      r_cfg(co).threshold := Cat(0.U(32 - log2Ceil(p.nPlicPrio)), io.b_regmem.wdata(log2Ceil(p.nPlicPrio) - 1, 0))
    }  
  }

  // Config: Claim
  for (ca <- 0 until (p.nPlicCause32b * 32)) {
    w_wclaim_clear(ca) := true.B
  }
  
  for (co <- 0 until p.nPlicContext) {
    for (ca <- 0 until p.nPlicCause) {
      when (io.b_regmem.wcfg(co)(1) & (ca.U === io.b_regmem.wdata)) {
        w_wclaim_clear(ca) := false.B
      }
    }
  }


  // ------------------------------
  //          HARDWIRED 0
  // ------------------------------
  // Priority
  r_sip(0) := 0.U
  for (ca <- 1 until p.nPlicCause) {
    if (!p.nPlicCauseUse(ca)) r_sip(ca) := 0.U
  }

  // Attribute
  r_attr(0) := 0.U
  for (ca <- 1 until p.nPlicCause) {
    if (!p.nPlicCauseUse(ca)) r_attr(ca) := 0.U
  }

  // ------------------------------
  //             READ
  // ------------------------------
  io.b_regmem.sip := r_sip
  io.b_regmem.ip := r_ip
  io.b_regmem.attr := r_attr
  io.b_regmem.en := r_en
  io.b_regmem.cfg := r_cfg

  // ******************************
  //        PENDING INTERRUPTS
  // ******************************
  val w_ip = Wire(Vec(p.nPlicCause32b * 32, Bool()))

  w_ip := (r_ip.asUInt).asBools

  for (ca <- 0 until p.nPlicCause32b * 32) {
    if (ca < p.nPlicCause) {
      r_irq(ca) := io.i_irq(ca)
      switch (r_attr(ca)(2, 1)) {
        is (TRIG.PLEVEL) {
          w_ip(ca) := io.i_irq(ca)
        }
        is (TRIG.PEDGE) {
          w_ip(ca) := ~r_irq(ca) & io.i_irq(ca)
        }
        is (TRIG.NLEVEL) {
          w_ip(ca) := ~io.i_irq(ca)
        }
        is (TRIG.NEDGE) {
          w_ip(ca) := r_irq(ca) & ~io.i_irq(ca)
        }
      }
    } else {
      r_irq(ca) := false.B
      w_ip(ca) := false.B
    }    
  }

//  r_ip := (Cat(Fill(p.nPlicCause - 1, 1.B), 0.U(1.W)) & (w_ip.asUInt | ((r_ip.asUInt ^ w_wip_clear.asUInt) & r_ip.asUInt))).asTypeOf(r_ip)
  r_ip := (Cat(Fill(p.nPlicCause - 1, 1.B), 0.U(1.W)) & (w_ip.asUInt | ((r_ip.asUInt ^ w_wclaim_clear.asUInt) & r_ip.asUInt))).asTypeOf(r_ip)

  // ******************************
  //        SELECT INTERRUPT
  // ******************************
  val w_pav = Wire(Vec(p.nPlicContext, Vec(p.nPlicPrio, Bool())))
  val w_pid = Wire(Vec(p.nPlicContext, Vec(p.nPlicPrio, UInt(log2Ceil(p.nPlicCause).W))))

  // ------------------------------
  //         PRIORITY SORT
  // ------------------------------
  for (co <- 0 until p.nPlicContext) {
    val m_prio = Seq.fill(p.nPlicPrio){Module(new PlicPriority(p))}

    for (pr <- 0 until p.nPlicPrio) {
      m_prio(pr).io.i_prio := pr.U
      m_prio(pr).io.i_ip := r_ip.asUInt
      m_prio(pr).io.i_en := r_en(co).asUInt
      m_prio(pr).io.i_sip := r_sip

      w_pav(co)(pr) := m_prio(pr).io.o_pav
      w_pid(co)(pr) := m_prio(pr).io.o_pid
    }

    w_pav(co)(0) := false.B
    w_pid(co)(0) := 0.U
  }

  // ------------------------------
  //            SELECT
  // ------------------------------
  val w_pprio = Wire(Vec(p.nPlicContext, UInt(log2Ceil(p.nPlicPrio).W)))

  for (co <- 0 until p.nPlicContext) {
    w_pprio(co) := PriorityEncoder(Reverse(w_pav(co).asUInt))

    w_rclaim_id(co) := w_pid(co)((p.nPlicPrio - 1).U - w_pprio(co))
  }  

  // ******************************
  //             CORE
  // ******************************
  for (co <- 0 until p.nPlicContext) {
    io.o_irq_ei(co) := w_pav(co).asUInt.orR & (w_pprio(co) > r_cfg(co).threshold)
  }  
}

object PlicPriority extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new PlicPriority(PlicConfigBase), args)
}

object Plic extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Plic(PlicConfigBase), args)
}

