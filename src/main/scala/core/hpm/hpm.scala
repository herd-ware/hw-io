/*
 * File: hpm.scala
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-21 04:48:48 pm
 * Modified By: Mathieu Escouteloup
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
    r_hpc(h).instret := r_hpc(h).instret + io.i_pipe(h).instret
    
    for (str <- concat(p.isHpmAct, p.hasHpmMap)) {
      if (List("ALU", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).alu := r_hpc(h).alu + io.i_pipe(h).alu
      }
      if (List("BR", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).br := r_hpc(h).br + io.i_pipe(h).br
      }
      if (List("MISPRED", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).mispred := r_hpc(h).mispred + io.i_pipe(h).mispred
      }
      if (List("LD", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).ld := r_hpc(h).ld + io.i_pipe(h).ld
      }
      if (List("ST", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).st := r_hpc(h).st + io.i_pipe(h).st
      }
      if (List("L1IHIT", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l1ihit := r_hpc(h).l1ihit + io.i_mem(h).l1ihit
      }
      if (List("L1IPFTCH", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l1ipftch := r_hpc(h).l1ipftch + io.i_mem(h).l1ipftch
      }
      if (List("L1IMISS", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l1imiss := r_hpc(h).l1imiss + io.i_mem(h).l1imiss
      }
      if (List("L1DHIT", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l1dhit := r_hpc(h).l1dhit + io.i_mem(h).l1dhit
      }
      if (List("L1DPFTCH", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l1dpftch := r_hpc(h).l1dpftch + io.i_mem(h).l1dpftch
      }
      if (List("L1DMISS", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l1dmiss := r_hpc(h).l1dmiss + io.i_mem(h).l1dmiss
      }
      if (List("L2HIT", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l2hit := r_hpc(h).l2hit + io.i_mem(h).l2hit
      }
      if (List("L2PFTCH", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l2pftch := r_hpc(h).l2pftch + io.i_mem(h).l2pftch
      }
      if (List("L2MISS", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).l2miss := r_hpc(h).l2miss + io.i_mem(h).l2miss
      }
      if (List("RDCYCLE", "ALL").contains(str.toUpperCase())) {
        r_hpc(h).rdcycle := r_hpc(h).rdcycle + io.i_pipe(h).rdcycle
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
    io.o_csr(h)(4) := r_hpc(h).br
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
  }  

  // ------------------------------
  //            CUSTOM
  // ------------------------------
  for (h <- 0 until p.nHart) {
    for (nm <- 0 until p.nHpmMap) {
      (p.hasHpmMap(nm).toUpperCase()) match {
        case "ALU"      => {io.o_csr(h)(3 + nm) := r_hpc(h).alu}
        case "BR"       => {io.o_csr(h)(3 + nm) := r_hpc(h).br}
        case "CYCLE"    => {io.o_csr(h)(3 + nm) := r_hpc(h).cycle}
        case "INSTRET"  => {io.o_csr(h)(3 + nm) := r_hpc(h).instret}
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
        case "ST"       => {io.o_csr(h)(3 + nm) := r_hpc(h).st}
        case "RDCYCLE"  => {io.o_csr(h)(3 + nm) := r_hpc(h).rdcycle}
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