/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:11:18 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.pltf.regmem

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

import herd.common.gen._
import herd.common.mem.mb4s._
import herd.io.pltf.plic._


trait RegMemParams extends Mb4sMemParams 
                  with GenParams
                  with PlicParams {
  def pPort: Array[Mb4sParams]

  def debug: Boolean  
  def nDataByte: Int = pPort(0).nDataByte
  def nDataBit: Int = nDataByte * 8
  def nHart: Int = pPort(0).nHart
  def nHart32b: Int = {
    if (nDataBit >= 64) {
      return (nHart + 64 - 1) / 32
    } else {
      return (nHart + 32 - 1) / 32
    }    
  }
  def nHart64b: Int = (nHart + 64 - 1) / 64
  def nByte: String = "04010000"
  def nAddrBit: Int
  def nAddrBase: String
  def readOnly: Boolean = pPort(0).readOnly

  def useCeps: Boolean = pPort(0).useDome
  def nCepsTrapLvl: Int
  def useDome: Boolean = pPort(0).useDome
  def nDome: Int = pPort(0).nDome
  def multiDome: Boolean = pPort(0).multiDome
  def nPart: Int = pPort(0).nPart

  def useReqReg: Boolean
  def nPlicContext: Int = {
    if (useCeps) {
      return nHart * nCepsTrapLvl
    } else {
      return nHart
    }
  }
  def nPlicPrio: Int
  def nGpio: Int
  def nGpio32b: Int = (nGpio + 32 - 1) / 32
  def nUart: Int
  def nPTimer: Int 
  def useSpiFlash: Boolean 
  def usePs2Keyboard: Boolean
  def nSpi: Int 
  def nI2c: Int 
  def nPlicCauseUse: Array[Boolean] = CAUSE.USE(
    nUart = nUart,
    nPTimer = nPTimer
  )
}

case class RegMemConfig (
  pPort: Array[Mb4sParams],

  debug: Boolean,
  nAddrBit: Int,
  nAddrBase: String,

  nCepsTrapLvl: Int,

  useReqReg: Boolean,
  nPlicPrio: Int,
  nGpio: Int,
  nUart: Int,
  nPTimer: Int,
  useSpiFlash: Boolean,
  usePs2Keyboard: Boolean,
  nSpi: Int, 
  nI2c: Int 
) extends RegMemParams
