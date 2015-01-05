# sqs-comp

An SQS Client component

```clojure
(ns foo
  (:require [sqs-comp.client :as c]
            [foo.queue :as q]))

(defn ->System []
  (component/system-map
    :client (component/using
              (c/make-sqs-client)
              [:config])
    :queue (component/using
             (q/make-queue-loop)
             [:config :client])))
```

## License

Copyright © 2015 Aaron France

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
