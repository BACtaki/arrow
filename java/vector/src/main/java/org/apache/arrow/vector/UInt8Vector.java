/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.arrow.vector;

import static org.apache.arrow.vector.NullCheckingForGet.NULL_CHECKING_ENABLED;

import java.math.BigInteger;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.complex.impl.UInt8ReaderImpl;
import org.apache.arrow.vector.complex.reader.FieldReader;
import org.apache.arrow.vector.holders.NullableUInt8Holder;
import org.apache.arrow.vector.holders.UInt8Holder;
import org.apache.arrow.vector.types.Types.MinorType;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.util.TransferPair;

import io.netty.buffer.ArrowBuf;

/**
 * UInt8Vector implements a fixed width vector (8 bytes) of
 * integer values which could be null. A validity buffer (bit vector) is
 * maintained to track which elements in the vector are null.
 */
public class UInt8Vector extends BaseFixedWidthVector implements BaseIntVector {
  private static final byte TYPE_WIDTH = 8;
  private final FieldReader reader;

  public UInt8Vector(String name, BufferAllocator allocator) {
    this(name, FieldType.nullable(MinorType.UINT8.getType()), allocator);
  }

  public UInt8Vector(String name, FieldType fieldType, BufferAllocator allocator) {
    super(name, allocator, fieldType, TYPE_WIDTH);
    reader = new UInt8ReaderImpl(UInt8Vector.this);
  }

  @Override
  public FieldReader getReader() {
    return reader;
  }

  @Override
  public MinorType getMinorType() {
    return MinorType.UINT8;
  }


  /*----------------------------------------------------------------*
   |                                                                |
   |          vector value retrieval methods                        |
   |                                                                |
   *----------------------------------------------------------------*/
  private static final BigInteger SAFE_CONVERSION_MASK = new BigInteger("ffffffffffffffff", 16);

  /**
   * Given a data buffer, get the value stored at a particular position
   * in the vector.
   *
   * <p>To avoid overflow, the returned type is one step up from the signed
   * type.
   *
   * <p>This method is mainly meant for integration tests.
   *
   * @param buffer data buffer
   * @param index position of the element.
   * @return value stored at the index.
   */
  public static BigInteger getNoOverflow(final ArrowBuf buffer, final int index) {
    BigInteger l =  BigInteger.valueOf(buffer.getLong(index * TYPE_WIDTH));
    return SAFE_CONVERSION_MASK.and(l);
  }


  /**
   * Get the element at the given index from the vector.
   *
   * @param index   position of element
   * @return element at given index
   */
  public long get(int index) throws IllegalStateException {
    if (NULL_CHECKING_ENABLED && isSet(index) == 0) {
      throw new IllegalStateException("Value at index is null");
    }
    return valueBuffer.getLong(index * TYPE_WIDTH);
  }

  /**
   * Get the element at the given index from the vector and
   * sets the state in holder. If element at given index
   * is null, holder.isSet will be zero.
   *
   * @param index   position of element
   */
  public void get(int index, NullableUInt8Holder holder) {
    if (isSet(index) == 0) {
      holder.isSet = 0;
      return;
    }
    holder.isSet = 1;
    holder.value = valueBuffer.getLong(index * TYPE_WIDTH);
  }

  /**
   * Same as {@link #get(int)}.
   *
   * @param index   position of element
   * @return element at given index
   */
  public Long getObject(int index) {
    if (isSet(index) == 0) {
      return null;
    } else {
      return valueBuffer.getLong(index * TYPE_WIDTH);
    }
  }

  /**
   * Returns the value stored at index without the potential for overflow.
   *
   * @param index   position of element
   * @return element at given index
   */
  public BigInteger getObjectNoOverflow(int index) {
    if (isSet(index) == 0) {
      return null;
    } else {
      return getNoOverflow(valueBuffer, index);
    }
  }

  /**
   * Copy a value and validity setting from fromIndex in <code>from</code> to this
   * Vector at thisIndex.
   */
  public void copyFrom(int fromIndex, int thisIndex, UInt8Vector from) {
    BitVectorHelper.setValidityBit(validityBuffer, thisIndex, from.isSet(fromIndex));
    final long value = from.valueBuffer.getLong(fromIndex * TYPE_WIDTH);
    valueBuffer.setLong(thisIndex * TYPE_WIDTH, value);
  }

  /**
   * Same as {@link #copyFrom(int, int, UInt8Vector)} but reallocates if thisIndex is
   * larger then current capacity.
   */
  public void copyFromSafe(int fromIndex, int thisIndex, UInt8Vector from) {
    handleSafe(thisIndex);
    copyFrom(fromIndex, thisIndex, from);
  }


  /*----------------------------------------------------------------*
   |                                                                |
   |          vector value setter methods                           |
   |                                                                |
   *----------------------------------------------------------------*/


  private void setValue(int index, long value) {
    valueBuffer.setLong(index * TYPE_WIDTH, value);
  }

  /**
   * Set the element at the given index to the given value.
   *
   * @param index   position of element
   * @param value   value of element
   */
  public void set(int index, long value) {
    BitVectorHelper.setValidityBitToOne(validityBuffer, index);
    setValue(index, value);
  }

  /**
   * Set the element at the given index to the value set in data holder.
   * If the value in holder is not indicated as set, element in the
   * at the given index will be null.
   *
   * @param index   position of element
   * @param holder  nullable data holder for value of element
   */
  public void set(int index, NullableUInt8Holder holder) throws IllegalArgumentException {
    if (holder.isSet < 0) {
      throw new IllegalArgumentException();
    } else if (holder.isSet > 0) {
      BitVectorHelper.setValidityBitToOne(validityBuffer, index);
      setValue(index, holder.value);
    } else {
      BitVectorHelper.setValidityBit(validityBuffer, index, 0);
    }
  }

  /**
   * Set the element at the given index to the value set in data holder.
   *
   * @param index   position of element
   * @param holder  data holder for value of element
   */
  public void set(int index, UInt8Holder holder) {
    BitVectorHelper.setValidityBitToOne(validityBuffer, index);
    setValue(index, holder.value);
  }

  /**
   * Same as {@link #set(int, long)} except that it handles the
   * case when index is greater than or equal to existing
   * value capacity {@link #getValueCapacity()}.
   *
   * @param index   position of element
   * @param value   value of element
   */
  public void setSafe(int index, long value) {
    handleSafe(index);
    set(index, value);
  }

  /**
   * Same as {@link #set(int, NullableUInt8Holder)} except that it handles the
   * case when index is greater than or equal to existing
   * value capacity {@link #getValueCapacity()}.
   *
   * @param index   position of element
   * @param holder  nullable data holder for value of element
   */
  public void setSafe(int index, NullableUInt8Holder holder) throws IllegalArgumentException {
    handleSafe(index);
    set(index, holder);
  }

  /**
   * Same as {@link #set(int, UInt8Holder)} except that it handles the
   * case when index is greater than or equal to existing
   * value capacity {@link #getValueCapacity()}.
   *
   * @param index   position of element
   * @param holder  data holder for value of element
   */
  public void setSafe(int index, UInt8Holder holder) {
    handleSafe(index);
    set(index, holder);
  }

  /**
   * Set the element at the given index to null.
   *
   * @param index   position of element
   */
  public void setNull(int index) {
    handleSafe(index);
    // not really needed to set the bit to 0 as long as
    // the buffer always starts from 0.
    BitVectorHelper.setValidityBit(validityBuffer, index, 0);
  }

  /** Sets value at index is isSet is positive otherwise sets the index to invalid/null. */
  public void set(int index, int isSet, long value) {
    if (isSet > 0) {
      set(index, value);
    } else {
      BitVectorHelper.setValidityBit(validityBuffer, index, 0);
    }
  }

  /**
   * Same as {@link #set(int, int, long)} but will reallocate if index is greater than current capacity.
   */
  public void setSafe(int index, int isSet, long value) {
    handleSafe(index);
    set(index, isSet, value);
  }


  /*----------------------------------------------------------------*
   |                                                                |
   |                      vector transfer                           |
   |                                                                |
   *----------------------------------------------------------------*/


  @Override
  public TransferPair getTransferPair(String ref, BufferAllocator allocator) {
    return new TransferImpl(ref, allocator);
  }

  @Override
  public TransferPair makeTransferPair(ValueVector to) {
    return new TransferImpl((UInt8Vector) to);
  }

  @Override
  public void setEncodedValue(int index, int value) {
    this.setSafe(index, value);
  }

  private class TransferImpl implements TransferPair {
    UInt8Vector to;

    public TransferImpl(String ref, BufferAllocator allocator) {
      to = new UInt8Vector(ref, field.getFieldType(), allocator);
    }

    public TransferImpl(UInt8Vector to) {
      this.to = to;
    }

    @Override
    public UInt8Vector getTo() {
      return to;
    }

    @Override
    public void transfer() {
      transferTo(to);
    }

    @Override
    public void splitAndTransfer(int startIndex, int length) {
      splitAndTransferTo(startIndex, length, to);
    }

    @Override
    public void copyValueSafe(int fromIndex, int toIndex) {
      to.copyFromSafe(fromIndex, toIndex, UInt8Vector.this);
    }
  }
}
