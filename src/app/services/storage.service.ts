import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import {
  Category, Provider, Group, Tag, Product, User,
  CashRegisterSession, Sale, SystemConfig
} from '../models/domain.model';
import {
  INITIAL_CATEGORIES, INITIAL_CONFIG, INITIAL_GROUPS,
  INITIAL_PRODUCTS, INITIAL_PROVIDERS, INITIAL_TAGS, INITIAL_USERS
} from '../data/initial-data';

@Injectable({
  providedIn: 'root'
})
export class StorageService {
  private readonly PREFIX = 'akhana_';

  // Reactive Subjects
  private categoriesSubject = new BehaviorSubject<Category[]>([]);
  private providersSubject = new BehaviorSubject<Provider[]>([]);
  private groupsSubject = new BehaviorSubject<Group[]>([]);
  private tagsSubject = new BehaviorSubject<Tag[]>([]);
  private productsSubject = new BehaviorSubject<Product[]>([]);
  private usersSubject = new BehaviorSubject<User[]>([]);
  private currentRegisterSubject = new BehaviorSubject<CashRegisterSession | null>(null);
  private registerHistorySubject = new BehaviorSubject<CashRegisterSession[]>([]);
  private salesSubject = new BehaviorSubject<Sale[]>([]);
  private configSubject = new BehaviorSubject<SystemConfig>(INITIAL_CONFIG);

  constructor() {
    this.initializeStorage();
  }

  private initializeStorage(): void {
    const categories = this.getItem<Category[]>('categories', INITIAL_CATEGORIES);
    const providers = this.getItem<Provider[]>('providers', INITIAL_PROVIDERS);
    const groups = this.getItem<Group[]>('groups', INITIAL_GROUPS);
    const tags = this.getItem<Tag[]>('tags', INITIAL_TAGS);
    const products = this.getItem<Product[]>('products', INITIAL_PRODUCTS);
    const users = this.getItem<User[]>('users', INITIAL_USERS);
    const config = this.getItem<SystemConfig>('config', INITIAL_CONFIG);
    const sales = this.getItem<Sale[]>('sales', []);
    const registerHistory = this.getItem<CashRegisterSession[]>('registers', []);

    // Check active open register or create a default open register for day shift if none
    let currentRegister = this.getItem<CashRegisterSession | null>('current_register', null);
    if (!currentRegister) {
      currentRegister = {
        id: 'reg-01',
        registerNumber: 'Caja #01',
        openedAt: new Date().toISOString(),
        openedBy: 'Juan Pérez',
        initialAmount: 200.0,
        isOpen: true,
        cashSalesTotal: 0,
        qrSalesTotal: 0,
        expectedCash: 200.0,
        notes: 'Apertura inicial de turno diario'
      };
      this.setItem('current_register', currentRegister);
    }

    this.categoriesSubject.next(categories);
    this.providersSubject.next(providers);
    this.groupsSubject.next(groups);
    this.tagsSubject.next(tags);
    this.productsSubject.next(products);
    this.usersSubject.next(users);
    this.configSubject.next(config);
    this.salesSubject.next(sales);
    this.registerHistorySubject.next(registerHistory);
    this.currentRegisterSubject.next(currentRegister);
  }

  // Observable Streams
  get categories$(): Observable<Category[]> { return this.categoriesSubject.asObservable(); }
  get providers$(): Observable<Provider[]> { return this.providersSubject.asObservable(); }
  get groups$(): Observable<Group[]> { return this.groupsSubject.asObservable(); }
  get tags$(): Observable<Tag[]> { return this.tagsSubject.asObservable(); }
  get products$(): Observable<Product[]> { return this.productsSubject.asObservable(); }
  get users$(): Observable<User[]> { return this.usersSubject.asObservable(); }
  get currentRegister$(): Observable<CashRegisterSession | null> { return this.currentRegisterSubject.asObservable(); }
  get registerHistory$(): Observable<CashRegisterSession[]> { return this.registerHistorySubject.asObservable(); }
  get sales$(): Observable<Sale[]> { return this.salesSubject.asObservable(); }
  get config$(): Observable<SystemConfig> { return this.configSubject.asObservable(); }

  // Sync Getters
  getCategories(): Category[] { return this.categoriesSubject.value; }
  getProviders(): Provider[] { return this.providersSubject.value; }
  getGroups(): Group[] { return this.groupsSubject.value; }
  getTags(): Tag[] { return this.tagsSubject.value; }
  getProducts(): Product[] { return this.productsSubject.value; }
  getUsers(): User[] { return this.usersSubject.value; }
  getCurrentRegister(): CashRegisterSession | null { return this.currentRegisterSubject.value; }
  getSales(): Sale[] { return this.salesSubject.value; }
  getRegisterHistory(): CashRegisterSession[] { return this.registerHistorySubject.value; }
  getConfig(): SystemConfig { return this.configSubject.value; }

  // Updaters & Savers
  saveCategories(categories: Category[]): void {
    this.setItem('categories', categories);
    this.categoriesSubject.next(categories);
  }

  saveProviders(providers: Provider[]): void {
    this.setItem('providers', providers);
    this.providersSubject.next(providers);
  }

  saveGroups(groups: Group[]): void {
    this.setItem('groups', groups);
    this.groupsSubject.next(groups);
  }

  saveTags(tags: Tag[]): void {
    this.setItem('tags', tags);
    this.tagsSubject.next(tags);
  }

  saveProducts(products: Product[]): void {
    this.setItem('products', products);
    this.productsSubject.next(products);
  }

  saveCurrentRegister(register: CashRegisterSession | null): void {
    this.setItem('current_register', register);
    this.currentRegisterSubject.next(register);
  }

  saveRegisterHistory(history: CashRegisterSession[]): void {
    this.setItem('registers', history);
    this.registerHistorySubject.next(history);
  }

  saveSales(sales: Sale[]): void {
    this.setItem('sales', sales);
    this.salesSubject.next(sales);
  }

  saveConfig(config: SystemConfig): void {
    this.setItem('config', config);
    this.configSubject.next(config);
  }

  // Internal Storage helpers
  private getItem<T>(key: string, defaultValue: T): T {
    try {
      const raw = localStorage.getItem(this.PREFIX + key);
      if (!raw) {
        this.setItem(key, defaultValue);
        return defaultValue;
      }
      return JSON.parse(raw) as T;
    } catch {
      return defaultValue;
    }
  }

  private setItem<T>(key: string, value: T): void {
    try {
      localStorage.setItem(this.PREFIX + key, JSON.stringify(value));
    } catch (e) {
      console.error('LocalStorage write error', e);
    }
  }
}
