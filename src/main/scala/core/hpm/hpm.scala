/*
 * File: hpm.scala                                                             *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-11 05:46:24 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core.hpm

import chisel3._
import chisel3.util._
import Array._

import herd.common.core.{HpcBus,HpcPipelineBus,HpcMemoryBus}


class Hpm(p: HpmParams) extends Module {  
  val io = IO( new Bundle {
    val i_pipe = Input(Vec(p.nHart, new HpcPipelineBus()))
    val i_mem = Input(Vec(p.nHart, new HpcMemoryBus()))

    val o_hpc = Output(Vec(p.nHart, new HpcBus()))
    val o_csr = Output(Vec(p.nHart, Vec(32, UInt(64.W))))    
  })

  val init_hpc = Wire(Vec(p.nHart, new HpcBus()))

  init_hpc := 0.U.asTypeOf(init_hpc)

  val r_hpc = RegInit(init_hpc)

  io.o_hpc := r_hpc

  // ******************************
  //            UPDATE
  // ******************************
  r_hpc(0).cycle := r_hpc(0).cycle + 1.U
  r_hpc(0).time := r_hpc(0).time + 1.U
  for (h <- 0 until p.nHart) {
    r_hpc(h).instret := r_hpc(h).instret + PopCount(io.i_pipe(h).instret.asUInt)
    
    for (str <- concat(p.isHpmAct, p.hasHpmMap)) {
      if (List("ALU", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).alu := r_hpc(h).alu + PopCount(io.i_pipe(h).alu.asUInt)
      }
      if (List("BRU", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).bru := r_hpc(h).bru + PopCount(io.i_pipe(h).bru.asUInt)
      }
      if (List("JAL", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).jal := r_hpc(h).jal + PopCount(io.i_pipe(h).jal.asUInt)
      }
      if (List("JALR", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).jalr := r_hpc(h).jalr + PopCount(io.i_pipe(h).jalr.asUInt)
      }
      if (List("MISPRED", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).mispred := r_hpc(h).mispred + PopCount(io.i_pipe(h).mispred.asUInt)
      }
      if (List("LD", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).ld := r_hpc(h).ld + PopCount(io.i_pipe(h).ld.asUInt)
      }
      if (List("ST", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).st := r_hpc(h).st + PopCount(io.i_pipe(h).st.asUInt)
      }
      if (List("L1IHIT", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l1ihit := r_hpc(h).l1ihit + PopCount(io.i_mem(h).l1ihit.asUInt)
      }
      if (List("L1IPFTCH", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l1ipftch := r_hpc(h).l1ipftch + PopCount(io.i_mem(h).l1ipftch.asUInt)
      }
      if (List("L1IMISS", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l1imiss := r_hpc(h).l1imiss + PopCount(io.i_mem(h).l1imiss.asUInt)
      }
      if (List("L1DHIT", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l1dhit := r_hpc(h).l1dhit + PopCount(io.i_mem(h).l1dhit.asUInt)
      }
      if (List("L1DPFTCH", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l1dpftch := r_hpc(h).l1dpftch + PopCount(io.i_mem(h).l1dpftch.asUInt)
      }
      if (List("L1DMISS", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l1dmiss := r_hpc(h).l1dmiss + PopCount(io.i_mem(h).l1dmiss.asUInt)
      }
      if (List("L2HIT", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l2hit := r_hpc(h).l2hit + PopCount(io.i_mem(h).l2hit.asUInt)
      }
      if (List("L2PFTCH", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l2pftch := r_hpc(h).l2pftch + PopCount(io.i_mem(h).l2pftch.asUInt)
      }
      if (List("L2MISS", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l2miss := r_hpc(h).l2miss + PopCount(io.i_mem(h).l2miss.asUInt)
      }
      if (List("RDCYCLE", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).rdcycle := r_hpc(h).rdcycle + PopCount(io.i_pipe(h).rdcycle.asUInt)
      }
      if (List("SRCDEP", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).srcdep := r_hpc(h).srcdep + PopCount(io.i_pipe(h).srcdep.asUInt)
      }
      if (List("CFLUSH", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).cflush := r_hpc(h).cflush + PopCount(io.i_pipe(h).cflush.asUInt)
      }
    }    
  }  

  // ******************************
  //          CSR MAPPING
  // ******************************
  // ------------------------------
  //            DEFAULT
  // ------------------------------
  io.o_csr := DontCare
  for (h <- 0 until p.nHart) {
    io.o_csr(h)(0) := r_hpc(0).cycle
    io.o_csr(h)(1) := r_hpc(0).time
    io.o_csr(h)(2) := r_hpc(h).instret
    io.o_csr(h)(3) := r_hpc(h).alu
    io.o_csr(h)(4) := r_hpc(h).bru
    io.o_csr(h)(5) := r_hpc(h).mispred
    io.o_csr(h)(6) := r_hpc(h).ld
    io.o_csr(h)(7) := r_hpc(h).st
    io.o_csr(h)(8) := r_hpc(h).l1ihit
    io.o_csr(h)(9) := r_hpc(h).l1ipftch
    io.o_csr(h)(10) := r_hpc(h).l1imiss
    io.o_csr(h)(11) := r_hpc(h).l1dhit
    io.o_csr(h)(12) := r_hpc(h).l1dpftch
    io.o_csr(h)(13) := r_hpc(h).l1dmiss
    io.o_csr(h)(14) := r_hpc(h).l2hit
    io.o_csr(h)(15) := r_hpc(h).l2pftch
    io.o_csr(h)(16) := r_hpc(h).l2miss
    io.o_csr(h)(17) := r_hpc(h).l1dmiss
    io.o_csr(h)(18) := r_hpc(h).l2miss
    io.o_csr(h)(19) := r_hpc(h).rdcycle
    io.o_csr(h)(20) := r_hpc(h).jal
    io.o_csr(h)(21) := r_hpc(h).jalr
    io.o_csr(h)(22) := r_hpc(h).call    
    io.o_csr(h)(23) := r_hpc(h).ret 
    io.o_csr(h)(24) := r_hpc(h).srcdep
    io.o_csr(h)(25) := r_hpc(h).cflush
  }

  // ------------------------------
  //            CUSTOM
  // ------------------------------
  for (h <- 0 until p.nHart) {
    for (nm <- 0 until p.nHpmMap) {
      (p.hasHpmMap(nm).toUpperCase()) match {
        case "ALU"      => {io.o_csr(h)(3 + nm) := r_hpc(h).alu}
        case "BRU"      => {io.o_csr(h)(3 + nm) := r_hpc(h).bru}
        case "CALL"     => {io.o_csr(h)(3 + nm) := r_hpc(h).call}
        case "CFLUSH"   => {io.o_csr(h)(3 + nm) := r_hpc(h).cflush}
        case "CYCLE"    => {io.o_csr(h)(3 + nm) := r_hpc(h).cycle}
        case "INSTRET"  => {io.o_csr(h)(3 + nm) := r_hpc(h).instret}
        case "JAL"      => {io.o_csr(h)(3 + nm) := r_hpc(h).jal}
        case "JALR"     => {io.o_csr(h)(3 + nm) := r_hpc(h).jalr}
        case "L1IHIT"   => {io.o_csr(h)(3 + nm) := r_hpc(h).l1ihit}
        case "L1IMISS"  => {io.o_csr(h)(3 + nm) := r_hpc(h).l1imiss}
        case "L1IPFTCH" => {io.o_csr(h)(3 + nm) := r_hpc(h).l1ipftch}
        case "L1DHIT"   => {io.o_csr(h)(3 + nm) := r_hpc(h).l1dhit}
        case "L1DMISS"  => {io.o_csr(h)(3 + nm) := r_hpc(h).l1dmiss}
        case "L1DPFTCH" => {io.o_csr(h)(3 + nm) := r_hpc(h).l1dpftch}
        case "L2HIT"    => {io.o_csr(h)(3 + nm) := r_hpc(h).l2hit}
        case "L2MISS"   => {io.o_csr(h)(3 + nm) := r_hpc(h).l2miss}
        case "L2PFTCH"  => {io.o_csr(h)(3 + nm) := r_hpc(h).l2pftch}
        case "LD"       => {io.o_csr(h)(3 + nm) := r_hpc(h).ld}
        case "MISPRED"  => {io.o_csr(h)(3 + nm) := r_hpc(h).mispred}
        case "RDCYCLE"  => {io.o_csr(h)(3 + nm) := r_hpc(h).rdcycle}
        case "RET"      => {io.o_csr(h)(3 + nm) := r_hpc(h).ret}
        case "SRCDEP"   => {io.o_csr(h)(3 + nm) := r_hpc(h).srcdep}
        case "ST"       => {io.o_csr(h)(3 + nm) := r_hpc(h).st}
        case "TIME"     => {io.o_csr(h)(3 + nm) := r_hpc(h).time}
      }
    }
  }

  // ******************************
  //            CONFIG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(r_hpc)
  }
}

object Hpm extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Hpm(HpmConfigBase), args)
}