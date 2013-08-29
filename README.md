PyBitmessage statistics gatherer in clojure

Run once an hour, pulls information from the database updated by a slightly modified running PyBitmessage instance and stores them in a sqlite database before generating graphs showing the number of messages processed each hour:

![Day](https://raw.github.com/penten/bmstats/master/doc/day.png)
![10 Days](https://raw.github.com/penten/bmstats/master/doc/10day.png)
![10 Days](https://raw.github.com/penten/bmstats/master/doc/10day2.png)

This shows the amount of messages processed by my system at the time, and be different on other systems owing to differences in speed and order messages are propagated through the network.
