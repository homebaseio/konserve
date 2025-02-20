(ns konserve.compressor
  (:require [konserve.protocols :refer [PStoreSerializer -serialize -deserialize]]
            [konserve.utils :refer [invert-map]])
  (:import [net.jpountz.lz4 LZ4FrameOutputStream LZ4FrameInputStream]))

(defrecord NullCompressor [serializer]
  PStoreSerializer
  (-deserialize [_ read-handlers bytes]
    (-deserialize serializer read-handlers bytes))
  (-serialize [_ bytes write-handlers val]
    (-serialize serializer bytes write-handlers val)))

(defrecord UnsupportedLZ4Compressor [serializer]
  PStoreSerializer
  (-deserialize [_ read-handlers bytes]
    (throw (ex-info "Unsupported LZ4 compressor." {:bytes bytes})))
  (-serialize [_ bytes write-handlers val]
    (throw (ex-info "Unsupported LZ4 compressor." {:bytes bytes}))))

(defrecord Lz4Compressor [serializer]
  PStoreSerializer
  (-deserialize [_ read-handlers bytes]
    (let [lz4-byte (LZ4FrameInputStream. bytes)]
      (-deserialize serializer read-handlers lz4-byte)))
  (-serialize [_ bytes write-handlers val]
    (let [lz4-byte (LZ4FrameOutputStream. bytes)]
      (-serialize serializer lz4-byte write-handlers val)
      (.flush lz4-byte))))

(defn null-compressor [serializer]
  (NullCompressor. serializer))

(defn unsupported-lz4-compressor [serializer]
  (UnsupportedLZ4Compressor. serializer))

(defn lz4-compressor [serializer]
  (Lz4Compressor. serializer))

#?(:clj
   (defmacro native-image-build? []
     (try
       (and (Class/forName "org.graalvm.nativeimage.ImageInfo")
            #_(eval '(org.graalvm.nativeimage.ImageInfo/inImageBuildtimeCode)))
       (catch Exception _
         false))))

(def byte->compressor
  {0 null-compressor
   1 #?(:clj (try
              ;; LZ4 requires native code that breaks the native-image executable atm.
               (if (native-image-build?)
                 unsupported-lz4-compressor
                 lz4-compressor)
               (catch Exception _
                 lz4-compressor))
        :cljs unsupported-lz4-compressor)})

(def compressor->byte
  (invert-map byte->compressor))
