package net.orfjackal.dimdwarf.services

trait Service {
  def start()

  def process(message: Any)
}