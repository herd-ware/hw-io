/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:08:58 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.ps2

import chisel3._
import chisel3.experimental.IO
import chisel3.util._


object Ps2ConfigBase extends Ps2Config (
  debug = true,
  nDataByte = 4,
  useRegMem = false,
  nBufferDepth = 8
)

