(ns overtone.sc.synth
  (:import 
     (de.sciss.jcollider Server Constants UGenInfo UGen
                         Group Node 
                         GraphElem Control Constant
                         Synth SynthDef UGenChannel)
     (de.sciss.jcollider.gui SynthDefDiagram)
     (de.sciss.net OSCClient OSCBundle OSCMessage))
  (:use overtone.sc))

;; NOTES
;; It seems that the classes in sclang often times generate multiple UGen nodes for what seems like a single one.  For example, in reality a SinOsc ugen only has two inputs for frequency and phase, and then a MulAdd ugen is used to support the amplitude and dc-offset arguments.  Also, I think the control rate ugens that optionally take a completion action, for example envelopes that can free their containing synth once they are done, are also implemented using an additional ugen that is made to do just this freeing.  We should think about this and do something convenient to make our API as easy as possible.

;; Hmmmmmmm, not even sure how we can use these
(def action 
  {; free the enclosing synth
   :free Constants/kDoneFree 

   ; free synth and all other nodes in this group (before and after)
   :free-all Constants/kDoneFreeAll

   ; free this synth and all preceding nodes in this group
   :free-all-pre Constants/kDoneFreeAllPred 

   ; free this synth and all following nodes in this group
   :free-all-after Constants/kDoneFreeAllSucc 

   ; free the enclosing group and all nodes within it (including this synth)
   :free-group Constants/kDoneFreeGroup 

   ; free this synth and pause the preceding node
   :free-pause-pre Constants/kDoneFreePausePred 

   ; free this synth and pause the following node
   :free-pause-next Constants/kDoneFreePauseSucc 

   ; free both this synth and the preceding node
   :free-free-pre Constants/kDoneFreePred 

   ; free this synth; if the preceding node is a group then do g_freeAll on it, else free it
   :free-pre-group Constants/kDoneFreePredGroup 

   ; free this synth and if the preceding node is a group then do g_deepFree on it, else free it
   :free-pre-group-deep Constants/kDoneFreePredGroupDeep 

   ; free both this synth and the following node
   :free-free-next Constants/kDoneFreeSucc 

   ; free this synth; if the following node is a group then do g_freeAll on it, else free it
   :free-next-group Constants/kDoneFreeSuccGroup 

   ; free this synth and if the following node is a group then do g_deepFree on it, else free it
   :free-next-group-deep Constants/kDoneFreeSuccGroupDeep 

   ; do nothing when the UGen is finished
   :nothing Constants/kDoneNothing 

   ; pause the enclosing synth, but do not free it
   :pause Constants/kDonePause
   })

(defn ugen-arg [arg]
  {:name (.name arg)
   :min (.min arg)
   :max (.max arg)})

(defn ugen-info [name info]
  {:name name 
   :args (for [arg (.args info)] (ugen-arg arg))})

(defn ugens []
  (for [[name info] (UGenInfo/infos)] (ugen-info name info)))

(defn print-ugens []
  (doseq [ugen (ugens)]
    (println (:display-name ugen) ": [" (for [arg (:args ugen)] (:name arg)) "]")))

(defn find-ugen [name]
  (filter #(= name (:name %1)) (ugens)))

;; TODO: OK, this is getting repetitive... Make a macro or something.
(defn ugen-ir [args]
  (clojure.lang.Reflector/invokeStaticMethod "de.sciss.jcollider.UGen" "ir" (to-array args)))

(defn ugen-kr [args]
  (clojure.lang.Reflector/invokeStaticMethod "de.sciss.jcollider.UGen" "kr" (to-array args)))

(defn ugen-ar [args]
  (clojure.lang.Reflector/invokeStaticMethod "de.sciss.jcollider.UGen" "ar" (to-array args)))

(defn ugen-dr [args]
  (clojure.lang.Reflector/invokeStaticMethod "de.sciss.jcollider.UGen" "dr" (to-array args)))

(defn ugen-array [args]
  (clojure.lang.Reflector/invokeStaticMethod "de.sciss.jcollider.UGen" "array" (to-array args)))

(defn ugen-ctl-ir [args]
  (clojure.lang.Reflector/invokeStaticMethod "de.sciss.jcollider.Control" "ir" (to-array args)))

(defn ugen-ctl-kr [args]
  (clojure.lang.Reflector/invokeStaticMethod "de.sciss.jcollider.Control" "kr" (to-array args)))


; Convert various components of a synthdef to UGen objects
; * numbers to constants 
; * vectors to arrays 
(defn ugenify [args]
  (map (fn [arg]
         (cond
           (number? arg) (ugen-ir [(float arg)])
           (vector? arg) (ugen-array (ugenify arg))
           true          arg))
       args))

(defn kr [& args]
  (ugen-kr (ugenify args)))

(defn ar [& args]
  (ugen-ar (ugenify args)))

(defn dr [& args]
  (ugen-dr (ugenify args)))

(defn ctl-ir [args]
  (ugen-ctl-ir (ugenify args)))

(defn ctl-kr [args]
  (ugen-ctl-kr (ugenify args)))
  
;; TODO: Look into doing things with simple OSC messaging if creating client-side representations of synths is slow.  We can do it like this:
;s.sendMsg("/s_new", "MyFavoriteSynth", n = s.nextNodeID;);
;s.sendMsg("/n_free", n);
  
(defmacro defsynth [name node]
  `(def ~(symbol (str name)) (SynthDef. ~(str name) ~node)))

(defn voice [synth & [args]]
  (let [synth-name (cond 
                     (string? synth) synth
                     (= de.sciss.jcollider.SynthDef (type synth)) (.getName synth))
        arg-names (into-array (for [k (keys args)] (str k)))
        arg-names (if (empty? arg-names) (make-array String 0) arg-names)
        arg-vals  (into-array (for [v (vals args)] (float v)))
        arg-vals  (if (empty? arg-vals) (make-array (. Float TYPE) 0) arg-vals)]
    (Synth/newPaused synth-name arg-names arg-vals (.asTarget *s*) (:pause action))))

(defn play [sdef]
  (.play sdef (root)))

(defn free [sdef]
  (.free sdef (root)))

(defn visualize [sdef]
  (SynthDefDiagram. sdef))
