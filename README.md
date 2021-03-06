# sqs-comp

An SQS Client component

```clojure
(ns foo
  (:require [sqs-comp.client :as c]
            [foo.queue :as q]))

(defn ->System []
  (component/system-map
    :config (config/->conf
              config-path)
    :client (component/using
              (c/make-sqs-client)
              [:config])
    :queue (component/using
             (q/make-queue-loop)
             [:config :client])))
```

Has a dependency on a component which has a field called:

`conf` which has keys:

* `incoming-queue`
* `using-am?`
  * `aws-id`
  * `aws-secret`

And optionally:

* `region`

Such as:

```clojure
(defrecord configuration [conf]
  component/Lifecycle
  (start [c]
  (assoc conf
    :incoming-queue "some-string"
    :using-iam true
    :region "us-east-1"))
  (stop [c]
    ))
```


## License

Copyright © 2015 Aaron France

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
