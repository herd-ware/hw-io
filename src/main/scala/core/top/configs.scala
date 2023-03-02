/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 06:19:59 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

import herd.common.mem.mb4s._


object IOCoreConfigBase extends IOCoreConfig (
  pPort = Array(new Mb4sConfig (
    debug = true,
    readOnly = false,
    nHart = 2,
    nAddrBit = 32,
    useAmo = false,
    nDataByte = 4,
    useField = true,
    nField = 2,
    multiField = false
  )),

  debug = true,
  nAddrBit = 32,
  nAddrBase = "00000000",

  nChampTrapLvl = 2,

  useReqReg = false,
  nScratch = 4,
  nCTimer = 2
)
