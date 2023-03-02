/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 06:19:39 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core.regmem

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

import herd.common.gen._
import herd.common.mem.mb4s._


trait RegMemParams extends Mb4sMemParams 
                  with GenParams {
  def pPort: Array[Mb4sParams]
  
  def debug: Boolean  
  def nDataByte: Int = pPort(0).nDataByte
  def nDataBit: Int = nDataByte * 8
  def nByte: String = "00001000"
  def nAddrBit: Int
  def nAddrBase: String
  def readOnly: Boolean = pPort(0).readOnly

  def useChamp: Boolean = pPort(0).useField
  def nChampTrapLvl: Int
  def useField: Boolean = pPort(0).useField
  def nField: Int = pPort(0).nField
  def multiField: Boolean = pPort(0).multiField
  def nPart: Int = pPort(0).nPart

  def useReqReg: Boolean
  def nScratch: Int  
  def nCTimer: Int  
}

case class RegMemConfig (
  pPort: Array[Mb4sParams],

  debug: Boolean,
  nAddrBit: Int,
  nAddrBase: String,

  nChampTrapLvl: Int,

  useReqReg: Boolean,
  nScratch: Int,  
  nCTimer : Int
) extends RegMemParams
